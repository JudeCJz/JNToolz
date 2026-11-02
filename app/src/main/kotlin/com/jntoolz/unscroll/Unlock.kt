package com.jntoolz.unscroll

import kotlin.random.Random

/**
 * The text that must be typed by hand to leave Hard mode: funny 2-3 sentence anti-scroll text plus a fresh random code.
 * Typing is checked one character at a time, so a paste (or any multi-character insert) is refused.
 */
object Unlock {
    val SENTENCES = listOf(
        "I came for five minutes two hours ago. Now I am watching mud mansions. Peak cinema.",
        "My bed is cozy and reality is overrated. The cat videos need my supervision. Here we go.",
        "I solemnly swear this next swipe will fulfill me. It will not be another fake podcast. Surely.",
        "Important chores are waiting for me. But strangers arguing online need my attention first. Priorities.",
        "I will scroll until my phone hits my face in bed. That is the official bedtime signal. Goodnight.",
        "I am not procrastinating, this is vital meme research. My attention span is fried, but vibes are immaculate.",
        "Battery is at three percent and charger is far away. A true warrior scrolls until black screen. Witness me.",
        "Horizontal potato mode activated on the couch. Only my scrolling thumb has full energy. Unstoppable.",
        "Just one more video before I fix my whole life. That was four days ago. The algorithm wins.",
        "If doomscrolling was an Olympic sport I would take the gold. My thumb is built different. Let us roll.",
        "Trading my future for fifteen second clips. The brain rot is delicious. Send help immediately.",
        "Dopamine miners reporting for night duty. Digging for memes until sunrise. Do not disturb."
    )

    const val SENTENCE = "I came for five minutes two hours ago. Now I am watching mud mansions. Peak cinema."

    /** No look-alikes (0/O, 1/l/I), so the code is hard but fair to copy by eye. */
    private const val CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 8

    fun sentence(random: Random = Random.Default): String =
        SENTENCES[random.nextInt(SENTENCES.size)]

    fun code(random: Random = Random.Default): String =
        String(CharArray(CODE_LENGTH) { CHARS[random.nextInt(CHARS.length)] })

    /** The full text to type for [sentence] and [code]. */
    fun target(sentence: String, code: String): String = "$sentence $code"

    /** The full text to type for default sentence and [code]. */
    fun target(code: String): String = target(SENTENCE, code)

    /**
     * Whether [new] may follow [old]: one character typed at the end, or text deleted. Anything else
     * (a paste, a clipboard chip, a whole word from autocomplete, an edit in the middle) is refused.
     */
    fun accepts(old: String, new: String): Boolean = when {
        new.length < old.length -> old.startsWith(new)
        new.length == old.length + 1 -> new.startsWith(old)
        else -> new == old
    }

    /** Exact match, case and spaces included. */
    fun matches(typed: String, sentence: String, code: String): Boolean = typed == target(sentence, code)

    /** Exact match with default sentence. */
    fun matches(typed: String, code: String): Boolean = matches(typed, SENTENCE, code)
}
