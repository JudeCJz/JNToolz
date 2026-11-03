package com.jntoolz.unscroll

/** Which short-form feed the user is in. [label] is shown in the "blocked" toast. */
enum class ShortKind(val label: String) {
    SHORTS("Shorts"),
    REELS("Reels"),
    TIKTOK("TikTok"),
}

/** Pure matching of "is this screen short-form?" from view ids and selected tab names. No Android types. */
object ShortsRules {
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
    const val FACEBOOK = "com.facebook.katana"
    const val YOUTUBE_REVANCED = "app.revanced.android.youtube"
    const val INSTAGRAM_LITE = "com.instagram.lite"
    const val FACEBOOK_LITE = "com.facebook.lite"
    const val REDDIT = "com.reddit.frontpage"
    const val LINKEDIN = "com.linkedin.android"
    val TIKTOK = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    /** Clients that share a main app's tabs and rules. */
    private val ALIAS: Map<String, String> = mapOf(
        YOUTUBE_REVANCED to YOUTUBE,
        INSTAGRAM_LITE to INSTAGRAM,
        FACEBOOK_LITE to FACEBOOK,
    )

    private fun base(pkg: String): String = ALIAS[pkg] ?: pkg

    /** Apps whose short-form part is blocked. */
    val GUARDED: Set<String> = setOf(YOUTUBE, INSTAGRAM, FACEBOOK) + TIKTOK

    /** Apps with a Shorts / Reels bottom tab that can be covered before it is tapped. */
    val TABBED: Set<String> = setOf(YOUTUBE, INSTAGRAM, FACEBOOK)

    /** Apps the scroll-time warning watches (also every package the service sees). */
    val TIMED: Set<String> = GUARDED + setOf(REDDIT, LINKEDIN)

    /** Full view resource ids that only exist on a short-form player screen, per app. */
    private val IDS: Map<String, List<String>> = mapOf(
        YOUTUBE to listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
        ),
        INSTAGRAM to listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_video_container",
        ),
    )

    /** Normalised name of the bottom tab that opens the short-form feed, per app. */
    private val TABS: Map<String, Pair<String, ShortKind>> = mapOf(
        YOUTUBE to ("shorts" to ShortKind.SHORTS),
        INSTAGRAM to ("reels" to ShortKind.REELS),
        FACEBOOK to ("reels" to ShortKind.REELS),
    )

    /** The view ids worth looking up for [pkg]. */
    fun viewIds(pkg: String): List<String> {
        val b = base(pkg)
        val ids = IDS[b].orEmpty()
        return if (b == pkg) ids else ids + ids.map { it.replaceFirst("$b:", "$pkg:") }
    }

    /** The label of [pkg]'s short-form tab ("Shorts", "Reels"), or null when it has none. */
    fun tabLabel(pkg: String): String? = TABS[base(pkg)]?.second?.label

    /** The short-form kind behind [pkg]'s tab, or null. */
    fun tabKind(pkg: String): ShortKind? = TABS[base(pkg)]?.second

    /** "Reels, tab 3 of 5" / "Shorts tab" / " SHORTS " -> "reels" / "shorts" / "shorts". */
    fun normalizeTab(text: String): String =
        text.lowercase().substringBefore(',').trim().removeSuffix(" tab").trim()

    /**
     * The feed the user is in, or null for normal content. [viewIds] are the full ids found visible on
     * screen; [selectedTabTexts] are the texts / content descriptions of selected nodes.
     */
    fun classify(pkg: String, viewIds: Set<String>, selectedTabTexts: Collection<String>): ShortKind? {
        if (pkg in TIKTOK) return ShortKind.TIKTOK
        val (tab, kind) = TABS[base(pkg)] ?: return null
        if (ShortsRules.viewIds(pkg).any { it in viewIds }) return kind
        if (selectedTabTexts.any { normalizeTab(it) == tab }) return kind
        return null
    }

    fun isShortForm(pkg: String, viewIds: Set<String>, selectedTabTexts: Collection<String>): Boolean =
        classify(pkg, viewIds, selectedTabTexts) != null

    /**
     * Whether a clicked node (its view id and description / text) looks like a Shorts / Reel entry in [pkg].
     * Called for the clicked node and a few parents; allocation free.
     */
    fun clickLooksShort(pkg: String, viewId: String?, text: CharSequence?): Boolean = when (base(pkg)) {
        YOUTUBE -> has(viewId, "reel") || has(viewId, "shorts") || has(text, "shorts") || has(text, "play short")
        INSTAGRAM -> has(viewId, "clips") || has(viewId, "reel") || has(text, "reel")
        FACEBOOK -> has(viewId, "reel") || has(text, "reel")
        else -> false
    }

    private fun has(s: CharSequence?, part: String): Boolean = s != null && s.contains(part, ignoreCase = true)
}
