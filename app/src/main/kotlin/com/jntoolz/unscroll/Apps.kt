package com.jntoolz.unscroll

/**
 * How Unscroll guards an app.
 * TABS = block the short-video tab / player inside the app, plus the long-scroll warning.
 * WHOLE = the app is all short video: leave it when opened.
 * FEED = endless feed: long-scroll warning only.
 */
enum class Guard { TABS, WHOLE, FEED }

/** An addictive app with ONE switch. [packages] are the Android packages behind it. */
enum class AddictiveApp(val label: String, val packages: Set<String>, val guard: Guard) {
    YOUTUBE("YouTube", setOf(ShortsRules.YOUTUBE, ShortsRules.YOUTUBE_REVANCED), Guard.TABS),
    INSTAGRAM("Instagram", setOf(ShortsRules.INSTAGRAM), Guard.TABS),
    INSTAGRAM_LITE("Instagram Lite", setOf(ShortsRules.INSTAGRAM_LITE), Guard.TABS),
    FACEBOOK("Facebook", setOf(ShortsRules.FACEBOOK), Guard.TABS),
    FACEBOOK_LITE("Facebook Lite", setOf(ShortsRules.FACEBOOK_LITE), Guard.TABS),

    TIKTOK("TikTok", ShortsRules.TIKTOK + "com.zhiliaoapp.musically.go", Guard.WHOLE),
    MOJ("Moj", setOf("in.mohalla.video"), Guard.WHOLE),
    JOSH("Josh", setOf("com.eterno.shortvideos"), Guard.WHOLE),
    CHINGARI("Chingari", setOf("io.chingari.app"), Guard.WHOLE),
    ROPOSO("Roposo", setOf("com.roposo.android"), Guard.WHOLE),
    LIKEE("Likee", setOf("video.like"), Guard.WHOLE),
    KWAI("Kwai", setOf("com.kwai.video"), Guard.WHOLE),
    LEMON8("Lemon8", setOf("com.bd.nproject"), Guard.WHOLE),

    SNAPCHAT("Snapchat", setOf("com.snapchat.android"), Guard.FEED),
    X("X", setOf("com.twitter.android"), Guard.FEED),
    REDDIT("Reddit", setOf(ShortsRules.REDDIT), Guard.FEED),
    LINKEDIN("LinkedIn", setOf(ShortsRules.LINKEDIN), Guard.FEED),
    PINTEREST("Pinterest", setOf("com.pinterest"), Guard.FEED),
    THREADS("Threads", setOf("com.instagram.barcelona"), Guard.FEED),
    TUMBLR("Tumblr", setOf("com.tumblr"), Guard.FEED),
    NINEGAG("9GAG", setOf("com.ninegag.android.app"), Guard.FEED),
    QUORA("Quora", setOf("com.quora.android"), Guard.FEED),
    SHARECHAT("ShareChat", setOf("in.mohalla.sharechat"), Guard.FEED),
    BLUESKY("Bluesky", setOf("xyz.blueskyweb.app"), Guard.FEED),
    TWITCH("Twitch", setOf("tv.twitch.android.app"), Guard.FEED),
    MASTODON("Mastodon", setOf("org.joinmastodon.android"), Guard.FEED);

    /** Preference key of this app's switch. */
    val key: String get() = "app_" + name.lowercase()

    /**
     * The switch value for installs that predate the one-switch model, from the old keys. [flag] reads an old
     * boolean pref with a default. ON if any old flag for the app was on; apps that never had one are ON.
     */
    fun legacyOn(flag: (key: String, default: Boolean) -> Boolean): Boolean {
        val warn = flag("warn_" + name.lowercase(), flag("scroll_warning", true))
        return when (this) {
            YOUTUBE -> flag("block_shorts", true) || warn
            INSTAGRAM, FACEBOOK -> flag("block_reels", true) || warn
            TIKTOK -> flag("block_tiktok", true)
            REDDIT, LINKEDIN -> warn
            else -> true
        }
    }

    companion object {
        private val BY_PACKAGE: Map<String, AddictiveApp> =
            entries.flatMap { a -> a.packages.map { it to a } }.toMap()

        fun of(pkg: String): AddictiveApp? = BY_PACKAGE[pkg]

        /** Every package the catalogue covers. */
        val ALL_PACKAGES: Set<String> = BY_PACKAGE.keys

        /** The apps with at least one installed package, tabs first, then whole-app, then feeds, then by name. */
        fun installed(isInstalled: (String) -> Boolean): List<AddictiveApp> =
            entries.filter { a -> a.packages.any(isInstalled) }
                .sortedWith(compareBy<AddictiveApp>({ it.guard.ordinal }, { it.label.lowercase() }))
    }
}
