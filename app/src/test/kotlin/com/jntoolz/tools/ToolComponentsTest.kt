package com.jntoolz.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every tool component in the manifest is switched by exactly one tool, so none keeps running when its tool is off. */
class ToolComponentsTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val namespace = "com.jntoolz"

    /** Components shared by all tools: never disabled. */
    private val shared = setOf("com.jntoolz.MainActivity", "androidx.core.content.FileProvider")

    private fun manifest(): File =
        listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File).firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")

    private fun manifestComponents(): List<String> {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = f.newDocumentBuilder().parse(manifest())
        val app = doc.getElementsByTagName("application").item(0) as Element
        val out = ArrayList<String>()
        val kids = app.childNodes
        for (i in 0 until kids.length) {
            val e = kids.item(i) as? Element ?: continue
            if (e.tagName !in setOf("activity", "activity-alias", "receiver", "service", "provider")) continue
            val name = e.getAttributeNS(androidNs, "name")
            out += if (name.startsWith(".")) namespace + name else name
        }
        return out
    }

    @Test
    fun everyToolComponentBelongsToExactlyOneTool() {
        val inManifest = manifestComponents()
        assertTrue("manifest parse found nothing", inManifest.isNotEmpty())
        for (c in inManifest) {
            if (c in shared) continue
            val owners = Tool.entries.filter { c in it.components }
            assertEquals("$c must belong to exactly one tool, found $owners", 1, owners.size)
        }
    }

    @Test
    fun everyListedComponentIsInTheManifestAndNotShared() {
        val inManifest = manifestComponents().toSet()
        for (tool in Tool.entries) {
            for (c in tool.components) {
                assertTrue("$tool lists $c, which the manifest does not declare", c in inManifest)
                assertTrue("$tool must not disable shared $c", c !in shared)
            }
            assertEquals("$tool lists a component twice", tool.components.size, tool.components.toSet().size)
        }
    }

    @Test
    fun openToolIdsMap() {
        assertEquals(Tool.BATTERY, Tool.of("sot"))
        assertEquals(Tool.COUNTER, Tool.of("counter"))
        assertEquals(Tool.CALENDAR, Tool.of("calendar"))
        assertEquals(null, Tool.of("nope"))
        assertEquals(null, Tool.of(null))
        assertNotNull(Tool.of(Tool.BATTERY.id))
    }
}
