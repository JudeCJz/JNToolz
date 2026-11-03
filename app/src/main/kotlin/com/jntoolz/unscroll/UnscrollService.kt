package com.jntoolz.unscroll

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.jntoolz.R

/**
 * Keeps short-form feeds (Shorts, Reels, short-video apps) from opening and warns after long continuous scrolling.
 *
 * Blocking, earliest first: a transparent overlay over the Shorts / Reels tab eats the tap; a click on a
 * Shorts / Reel entry arms a fast check; a throttled content check is the fallback that presses Back
 * (Back, Back, Home, then gives up). Does nothing while the tool is off. Settings are cached in memory.
 * Reads only view ids and tab names; nothing is stored or sent.
 */
class UnscrollService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var store: UnscrollStore
    private var cfg = UnscrollConfig()
    private val session = ScrollSession()
    private val limiter = BlockLimiter()
    private val rect = Rect()

    private var leaving = false
    private var lastWhole = 0L
    private var lastInspect = 0L
    private var hotUntil = 0L
    private var inspectPkg = ""
    private var trailingPending = false
    private var tabSelected = false

    private var overlay: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayBox: TabBox? = null
    private var overlayPkg: String? = null
    private var overlayLabel = ""
    private var lastTap = 0L

    private val trailingRun = Runnable {
        trailingPending = false
        if (cfg.activeAt()) inspect(inspectPkg)
    }
    private val tickRun = Runnable { tick() }
    private val clickRun = Runnable { if (cfg.activeAt() && !leaving) inspect(inspectPkg) }

    override fun onServiceConnected() {
        store = UnscrollStore(this)
        store.watch { c ->
            cfg = c
            val p = overlayPkg
            if (!c.activeAt() || p == null || !c.blocksPackage(p)) hideOverlay()
            if (c.pausedAt(System.currentTimeMillis())) session.reset()
        }
        cfg = store.config
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val c = cfg
        if (!c.activeAt()) return
        val pkg = e.packageName?.toString() ?: return
        val type = e.eventType

        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (c.warns(pkg)) {
                val now = SystemClock.elapsedRealtime()
                if (session.onScroll(pkg, now, c.warnMinutes * 60_000L)) warnScrolling(pkg, c.warnMinutes)
            }
            return
        }
        if (!c.blocksPackage(pkg)) return

        if (AddictiveApp.of(pkg)?.guard == Guard.WHOLE) {
            val now = SystemClock.elapsedRealtime()
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !leaving && now - lastWhole > 1500) {
                lastWhole = now
                block(appName(pkg), pkg, whole = true)
            }
            return
        }
        when (type) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> onClicked(e, pkg)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                inspectPkg = pkg
                inspect(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                inspectPkg = pkg
                val now = SystemClock.elapsedRealtime()
                val gap = if (now < hotUntil) HOT_THROTTLE_MS else THROTTLE_MS
                if (now - lastInspect >= gap) inspect(pkg)
                else if (!trailingPending) {
                    // Do not lose the last change of a burst: look once more when the throttle ends.
                    trailingPending = true
                    main.postDelayed(trailingRun, gap - (now - lastInspect))
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        hideOverlay()
        main.removeCallbacksAndMessages(null)
        if (::store.isInitialized) store.unwatch()
    }

    /** A click on a Shorts / Reel entry (the node or a parent): arm a fast check, the first one right away. */
    private fun onClicked(e: AccessibilityEvent, pkg: String) {
        if (leaving) return
        var n: AccessibilityNodeInfo? = e.source
        var depth = 0
        var hit = false
        while (n != null && depth < CLICK_DEPTH) {
            if (ShortsRules.clickLooksShort(pkg, n.viewIdResourceName, n.contentDescription ?: n.text)) {
                hit = true
                break
            }
            n = n.parent
            depth++
        }
        if (!hit) return
        inspectPkg = pkg
        hotUntil = SystemClock.elapsedRealtime() + HOT_MS
        main.removeCallbacks(clickRun)
        main.postDelayed(clickRun, CLICK_CHECK_MS)
    }

    /**
     * One look at the foreground window of [pkg]: keep the tab overlay in place, and (unless already
     * leaving) block if a short-form screen is showing.
     */
    private fun inspect(pkg: String) {
        lastInspect = SystemClock.elapsedRealtime()
        val c = cfg
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != pkg || !c.blocksPackage(pkg)) {
            hideOverlay()
            return
        }
        val label = ShortsRules.tabLabel(pkg)
        val box = if (label != null) findTab(root, label) else null
        if (box != null) showOverlay(pkg, box, label.orEmpty()) else hideOverlay()
        if (leaving) return
        val kind = detect(root, pkg, label, tabSelected) ?: return
        block(kind.label, pkg, whole = false)
    }

    /** The short-form feed on screen in [pkg] right now, or null. */
    private fun kindNow(pkg: String): ShortKind? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != pkg) return null
        val label = ShortsRules.tabLabel(pkg)
        if (label != null) findTab(root, label)
        return detect(root, pkg, label, tabSelected)
    }

    private fun detect(root: AccessibilityNodeInfo, pkg: String, label: String?, selected: Boolean): ShortKind? {
        var ids: Set<String> = emptySet()
        for (id in ShortsRules.viewIds(pkg)) {
            val found = root.findAccessibilityNodeInfosByViewId(id)
            if (found.any { it.isVisibleToUser }) {
                if (ids.isEmpty()) ids = HashSet()
                (ids as HashSet).add(id)
            }
        }
        val sel = if (selected && label != null) listOf(label) else emptyList()
        return ShortsRules.classify(pkg, ids, sel)
    }

    /**
     * The on-screen box of the Shorts / Reels tab, found by its label (a framework-side text search, no
     * tree walk). Sets [tabSelected]. Null when the tab is not visible in a nav band.
     */
    private fun findTab(root: AccessibilityNodeInfo, label: String): TabBox? {
        tabSelected = false
        val key = ShortsRules.normalizeTab(label)
        for (n in root.findAccessibilityNodeInfosByText(label)) {
            val t = (n.contentDescription ?: n.text)?.toString() ?: continue
            if (t.length > 40 || ShortsRules.normalizeTab(t) != key || !n.isVisibleToUser) continue
            val box = boxOf(n) ?: continue
            var s: AccessibilityNodeInfo? = n
            var d = 0
            while (s != null && d < 3 && !tabSelected) {
                tabSelected = s.isSelected
                s = s.parent
                d++
            }
            return box
        }
        return null
    }

    /** The tab's tap area: its clickable ancestor (a few levels up) or the node itself, if either looks like a tab. */
    private fun boxOf(n: AccessibilityNodeInfo): TabBox? {
        val dm = resources.displayMetrics
        var p: AccessibilityNodeInfo? = n
        var d = 0
        while (p != null && d < 3) {
            if (p.isClickable) {
                p.getBoundsInScreen(rect)
                TabBox.snap(TabBox(rect.left, rect.top, rect.right, rect.bottom), dm.widthPixels, dm.heightPixels)?.let { return it }
                break
            }
            p = p.parent
            d++
        }
        n.getBoundsInScreen(rect)
        return TabBox.snap(TabBox(rect.left, rect.top, rect.right, rect.bottom), dm.widthPixels, dm.heightPixels)
    }

    /** Puts (or moves) the transparent touch-eater over [box]. */
    private fun showOverlay(pkg: String, box: TabBox, label: String) {
        overlayPkg = pkg
        overlayLabel = label
        val existing = overlay
        val lp = overlayParams
        if (existing != null && lp != null) {
            if (box != overlayBox) {
                lp.x = box.left
                lp.y = box.top
                lp.width = box.width
                lp.height = box.height
                try {
                    getSystemService(WindowManager::class.java).updateViewLayout(existing, lp)
                } catch (e: Exception) {
                    hideOverlay()
                    return
                }
                overlayBox = box
            }
            return
        }
        val v = View(this)
        v.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) onOverlayTap()
            true
        }
        val params = WindowManager.LayoutParams(
            box.width, box.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = box.left
            y = box.top
        }
        try {
            getSystemService(WindowManager::class.java).addView(v, params)
        } catch (e: Exception) {
            return
        }
        overlay = v
        overlayParams = params
        overlayBox = box
        main.removeCallbacks(tickRun)
        main.postDelayed(tickRun, TICK_MS)
    }

    private fun hideOverlay() {
        main.removeCallbacks(tickRun)
        val v = overlay ?: return
        try {
            getSystemService(WindowManager::class.java).removeView(v)
        } catch (e: Exception) {
            // Already gone.
        }
        overlay = null
        overlayParams = null
        overlayBox = null
        overlayPkg = null
    }

    /** Watchdog while the overlay shows: events stop when the user leaves the app, so look for that here. */
    private fun tick() {
        val pkg = overlayPkg
        if (pkg == null || !cfg.blocksPackage(pkg) || rootInActiveWindow?.packageName?.toString() != pkg) {
            hideOverlay()
            return
        }
        main.postDelayed(tickRun, TICK_MS)
    }

    private fun onOverlayTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTap < TAP_TOAST_MS) return
        lastTap = now
        store.countBlock()
        Toast.makeText(this, "$overlayLabel blocked", Toast.LENGTH_SHORT).show()
    }

    /** Toast, then Back; if still there shortly after, Back again, then Home. A [whole] app goes straight Home. */
    private fun block(what: String, pkg: String, whole: Boolean) {
        if (!limiter.allow(SystemClock.elapsedRealtime())) return
        leaving = true
        store.countBlock()
        Toast.makeText(this, "$what blocked", Toast.LENGTH_SHORT).show()
        if (whole) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            main.postDelayed({ leaving = false }, 800)
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({ recheck(pkg, 1) }, RECHECK_MS)
    }

    private fun recheck(pkg: String, attempt: Int) {
        if (!cfg.activeAt() || kindNow(pkg) == null) {
            leaving = false
            return
        }
        when (Escalation.step(attempt)) {
            Escalation.Step.BACK -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                main.postDelayed({ recheck(pkg, attempt + 1) }, RECHECK_MS)
            }
            Escalation.Step.HOME -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                main.postDelayed({ leaving = false }, RECHECK_MS)
            }
            null -> leaving = false
        }
    }

    private fun warnScrolling(pkg: String, minutes: Int) {
        val text = "${appName(pkg)}: $minutes min of scrolling"
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Scroll warnings", NotificationManager.IMPORTANCE_HIGH)
            )
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_unscroll_stat)
                .setContentTitle("Unscroll")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFY_BASE + (pkg.hashCode() and 0xFFFF), n)
        } catch (e: Exception) {
            // Notifications not allowed: the toast already warned.
        }
    }

    private fun appName(pkg: String): String = AddictiveApp.of(pkg)?.label ?: pkg.substringAfterLast('.')

    private companion object {
        const val THROTTLE_MS = 300L
        const val HOT_THROTTLE_MS = 80L
        const val HOT_MS = 2000L
        const val CLICK_CHECK_MS = 100L
        const val CLICK_DEPTH = 6
        const val TICK_MS = 500L
        const val TAP_TOAST_MS = 800L
        const val RECHECK_MS = 700L
        const val CHANNEL = "unscroll_warn"
        const val NOTIFY_BASE = 7000
    }
}
