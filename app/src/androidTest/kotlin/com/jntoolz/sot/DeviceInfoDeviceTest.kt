package com.jntoolz.sot

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceInfoDeviceTest {
    @Test
    fun testDeviceInfoTelemetry() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val name = DeviceInfo.deviceName(ctx)
        val os = DeviceInfo.osVersion()
        val cap = DeviceInfo.batteryDesignMah(ctx)
        val health = DeviceInfo.batteryHealth(ctx, emptyList())
        val age = DeviceInfo.deviceAge()
        val summary = DeviceInfo.summary(ctx, emptyList())

        Log.i("DEVICE_INFO_TEST", "=== SUMMARY ===")
        Log.i("DEVICE_INFO_TEST", summary)

        println("=== DEVICE INFO SUMMARY ===")
        println(summary)

        assertTrue("Expected Nothing Phone (4a) Pro, was: $name", name.contains("Nothing Phone (4a) Pro", ignoreCase = true))
        assertTrue("Expected Nothing OS, was: $os", os.contains("Nothing OS"))
        assertTrue("Expected >= 5000 mAh, was: $cap", cap >= 5000)
    }

    @Test
    fun testGraphTextGeneration() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val sn = Store.get(ctx).snap()
        val st = Stats.compute(sn.bat, sn.screen, false, System.currentTimeMillis())
        val devInfo = DeviceInfo.summary(ctx, sn.bat)
        val text = GraphExport.build(st, "today", sn.bat, sn.screen, sn.current, null, null, -1L, false, deviceHeader = devInfo)
        Log.i("CLIP_TEST", "=== EXPORT TEXT ===\n$text")
        assertTrue("Should contain Nothing Phone (4a) Pro", text.contains("Nothing Phone (4a) Pro"))
        assertTrue("Should contain 5,400 mAh", text.contains("5,400 mAh"))
        assertTrue("Should contain Nothing OS 5.0", text.contains("Nothing OS 5.0"))
        assertTrue("Should contain Device age", text.contains("Device age"))
    }
}
