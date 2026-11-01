package com.jntoolz.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The phone's own Nothing fonts in /system/fonts (present on Nothing phones), plus the system default. */
enum class NothingFont(val label: String, val file: String?) {
    NDOT_57("Ndot 57", "Ndot-57.otf"),
    NDOT_55("Ndot 55", "Ndot-55.otf"),
    NDOT_CAPS("Ndot Caps", "NDot57Caps.otf"),
    NDOT_77("Ndot 77", "Ndot77JPExtended.ttf"),
    NTYPE_HEADLINE("NType Headline", "NType82-Headline.otf"),
    NTYPE("NType", "NType82-Regular.otf"),
    LETTERA("Lettera", "LetteraMonoLL-Regular.otf"),
    CLOCK("Clock", "AndroidClock.ttf"),
    SYSTEM("System", null);

    /** Dot-matrix family (the counter draws its own dot digits when none of these is readable). */
    val isDot: Boolean get() = this == NDOT_57 || this == NDOT_55 || this == NDOT_CAPS || this == NDOT_77

    companion object {
        fun of(name: String?): NothingFont = entries.firstOrNull { it.name == name } ?: NDOT_57
    }
}

/** Loads each font once (thread safe); unreadable files fall back to a close system face. */
object NothingFonts {
    private const val DIR = "/system/fonts"

    private val loaded = ConcurrentHashMap<NothingFont, Typeface>()
    private val readable = ConcurrentHashMap<NothingFont, Boolean>()
    private val families = ConcurrentHashMap<NothingFont, FontFamily>()

    /** The font's typeface, or its fallback when the file is missing or unreadable. */
    fun typeface(f: NothingFont): Typeface = loaded.getOrPut(f) { load(f) ?: fallback(f) }

    fun family(f: NothingFont): FontFamily = families.getOrPut(f) { FontFamily(typeface(f)) }

    /** True when the real font file loads on this phone (SYSTEM always). */
    fun available(f: NothingFont): Boolean {
        if (f.file == null) return true
        readable[f]?.let { return it }
        typeface(f)
        return readable[f] == true
    }

    /**
     * Loads every font now (blocking file IO). Call it on a background thread (Dispatchers.IO) so
     * later [typeface] / [family] / [available] calls on the main thread are cache hits.
     */
    fun warm() {
        for (f in NothingFont.entries) family(f)
    }

    private fun load(f: NothingFont): Typeface? {
        val name = f.file ?: return Typeface.DEFAULT.also { readable[f] = true }
        val face = try {
            val file = File(DIR, name)
            if (file.canRead()) Typeface.createFromFile(file) else null
        } catch (e: Exception) {
            null
        }
        readable[f] = face != null
        return face
    }

    private fun fallback(f: NothingFont): Typeface = when (f) {
        NothingFont.NTYPE_HEADLINE, NothingFont.NTYPE -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        NothingFont.LETTERA -> Typeface.MONOSPACE
        NothingFont.SYSTEM -> Typeface.DEFAULT
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
}
