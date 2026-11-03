package com.jntoolz.unscroll

/**
 * When the accessibility service may be on. Banking apps refuse to run while any third-party accessibility
 * service is enabled, so the service is only switched on while a watched social app is in the foreground.
 * No Android types.
 */
object AppGate {
    /** Every app the service watches: the whole [AddictiveApp] catalogue. */
    val WATCHED: Set<String> = AddictiveApp.ALL_PACKAGES

    /** Whether the service should be on with [foreground] (null = unknown) in front. */
    fun shouldEnable(foreground: String?, mode: GuardMode, paused: Boolean): Boolean =
        mode.blocking && !paused && foreground in WATCHED
}
