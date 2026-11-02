package com.jntoolz.unscroll

/**
 * How firmly Unscroll holds on. SOFT and HARD block the same things; HARD only makes leaving harder: going
 * from HARD to SOFT or OFF needs the typed unlock (see [Unlock]). Everything else is instant, except that
 * going to HARD is confirmed first.
 */
enum class GuardMode {
    OFF, SOFT, HARD;

    /** Whether blocks and warnings run in this mode. */
    val blocking: Boolean get() = this != OFF

    /** What the user has to get through to go from this mode to [to]. */
    fun gateTo(to: GuardMode): Gate = when {
        to == this -> Gate.NONE
        this == HARD -> Gate.UNLOCK
        to == HARD -> Gate.CONFIRM
        else -> Gate.NONE
    }

    /** Whether going to [to] may happen right now, without a dialog. */
    fun canSwitch(to: GuardMode): Boolean = gateTo(to) == Gate.NONE

    /** NONE: switch now. CONFIRM: the "HARD MODE" warning first. UNLOCK: the typed unlock first. */
    enum class Gate { NONE, CONFIRM, UNLOCK }

    companion object {
        /** The saved mode; saves from before modes existed only had the "enabled" flag, and on reads as SOFT. */
        fun restore(saved: String?, legacyEnabled: Boolean): GuardMode =
            entries.firstOrNull { it.name == saved } ?: if (legacyEnabled) SOFT else OFF
    }
}
