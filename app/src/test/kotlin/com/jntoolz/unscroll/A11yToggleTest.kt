package com.jntoolz.unscroll

import org.junit.Assert.assertEquals
import org.junit.Test

class A11yToggleTest {
    private val me = "com.jntoolz/com.jntoolz.unscroll.UnscrollService"
    private val other = "com.other/com.other.Svc"

    @Test
    fun addsToEmptyAndNull() {
        assertEquals(me, A11yToggle.edit(null, me, true))
        assertEquals(me, A11yToggle.edit("", me, true))
    }

    @Test
    fun addKeepsOthersAndIsIdempotent() {
        assertEquals("$other:$me", A11yToggle.edit(other, me, true))
        assertEquals("$other:$me", A11yToggle.edit("$other:$me", me, true))
    }

    @Test
    fun removeOnlyOurEntry() {
        assertEquals(other, A11yToggle.edit("$me:$other", me, false))
        assertEquals(other, A11yToggle.edit(other, me, false))
        assertEquals("", A11yToggle.edit(me, me, false))
        assertEquals("", A11yToggle.edit(null, me, false))
    }

    @Test
    fun shortFormAndCaseCountAsSame() {
        assertEquals(other, A11yToggle.edit("com.jntoolz/.unscroll.UnscrollService:$other", "com.jntoolz/com.jntoolz.unscroll.UnscrollService", false))
        assertEquals(other, A11yToggle.edit("$other:COM.JNTOOLZ/com.jntoolz.unscroll.UnscrollService", me, false))
    }
}
