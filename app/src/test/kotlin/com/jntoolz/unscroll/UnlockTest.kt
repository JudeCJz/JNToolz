package com.jntoolz.unscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnlockTest {
    @Test
    fun codeIsFreshAndFair() {
        val a = Unlock.code(Random(1))
        val b = Unlock.code(Random(2))
        assertEquals(Unlock.CODE_LENGTH, a.length)
        assertNotEquals(a, b)
        assertTrue(a.none { it in "0O1lI" })
    }

    @Test
    fun typingOneCharAtATimeIsAccepted() {
        assertTrue(Unlock.accepts("", "I"))
        assertTrue(Unlock.accepts("I kn", "I kno"))
        assertTrue(Unlock.accepts("I kno", "I kn")) // backspace
        assertTrue(Unlock.accepts("I kno", "")) // clear
    }

    @Test
    fun pastesAndJumpsAreRefused() {
        assertFalse(Unlock.accepts("", "I know I am"))
        assertFalse(Unlock.accepts("I ", "I know"))
        assertFalse(Unlock.accepts("I kno", "X kno")) // edit in the middle
        assertFalse(Unlock.accepts("abc", "abXc"))
        assertFalse(Unlock.accepts("abc", "xb")) // not a deletion of the end
    }

    @Test
    fun matchIsExact() {
        val code = "abCD23efGH45jkMN"
        assertTrue(Unlock.matches("${Unlock.SENTENCE} $code", code))
        assertFalse(Unlock.matches("${Unlock.SENTENCE} ${code.lowercase()}", code))
        assertFalse(Unlock.matches("${Unlock.SENTENCE}  $code", code))
    }

    @Test
    fun sentenceIsRandomizedAndMatches() {
        val s1 = Unlock.sentence(Random(1))
        val s2 = Unlock.sentence(Random(5))
        assertTrue(s1 in Unlock.SENTENCES)
        assertTrue(s2 in Unlock.SENTENCES)
        val code = Unlock.code(Random(1))
        val target = Unlock.target(s1, code)
        assertTrue(Unlock.matches(target, s1, code))
        assertFalse(Unlock.matches(target, "A different sentence", code))
    }
}
