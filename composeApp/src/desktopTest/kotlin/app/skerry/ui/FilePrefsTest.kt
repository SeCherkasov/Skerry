package app.skerry.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilePrefsTest {

    private fun temp(): Path = Files.createTempDirectory("skerry-prefs")

    @Test
    fun boolRoundTripsAndDefaultsWhenMissing() {
        val prefs = FilePrefs(temp())
        // Файла нет → дефолт как есть.
        assertTrue(prefs.bool("info_panel", true))
        assertFalse(prefs.bool("terminal_show_title", false))
        prefs.set("info_panel", false)
        assertFalse(prefs.bool("info_panel", true))
        prefs.set("terminal_show_title", true)
        assertTrue(prefs.bool("terminal_show_title", false))
    }

    @Test
    fun boolKeepsLegacyGarbageSemantics() {
        val dir = temp()
        val prefs = FilePrefs(dir)
        Files.writeString(dir.resolve("flag"), "garbage")
        // Прежняя семантика read*: default=true читает `!= "0"` (мусор → true),
        // default=false читает `== "1"` (мусор → false).
        assertTrue(prefs.bool("flag", true))
        assertFalse(prefs.bool("flag", false))
    }

    @Test
    fun intRoundTripsAndDefaultsOnGarbage() {
        val dir = temp()
        val prefs = FilePrefs(dir)
        assertEquals(8, prefs.int("recent_limit", 8))
        prefs.set("recent_limit", 3)
        assertEquals(3, prefs.int("recent_limit", 8))
        Files.writeString(dir.resolve("recent_limit"), "not-a-number")
        assertEquals(8, prefs.int("recent_limit", 8))
    }

    @Test
    fun idParsesAndFallsBackWhenParseThrows() {
        val prefs = FilePrefs(temp())
        prefs.set("auto_lock", "5m")
        assertEquals("5m", prefs.id("auto_lock", "1m") { it })
        // Файла нет → дефолт; parse бросает → дефолт.
        assertEquals("1m", prefs.id("missing", "1m") { it })
        prefs.set("auto_lock", "junk")
        assertEquals("1m", prefs.id("auto_lock", "1m") { if (it == "junk") error("unknown") else it })
    }

    @Test
    fun linesRoundTripsTrimmingBlanksAndOrder() {
        val prefs = FilePrefs(temp())
        assertEquals(emptyList(), prefs.lines("recent_connections"))
        prefs.setLines("recent_connections", listOf("b", "a", "c"))
        assertEquals(listOf("b", "a", "c"), prefs.lines("recent_connections"))
        prefs.setLines("recent_connections", emptyList())
        assertEquals(emptyList(), prefs.lines("recent_connections"))
    }

    @Test
    fun setLinesDropsValuesWithNewlines() {
        val prefs = FilePrefs(temp())
        prefs.setLines("collapsed_groups", listOf("ok", "bad\nsplit", "bad\rtoo", "fine"))
        assertEquals(listOf("ok", "fine"), prefs.lines("collapsed_groups"))
    }

    @Test
    fun writesCreateDirectoryAndSwallowFailures() {
        val dir = temp().resolve("nested")
        val prefs = FilePrefs(dir)
        prefs.set("info_panel", true) // каталога ещё нет — создаётся сам
        assertTrue(prefs.bool("info_panel", false))
        // Запись поверх нечитаемого пути не бросает (best-effort).
        val filePrefs = FilePrefs(dir.resolve("info_panel")) // «каталог» — существующий файл
        filePrefs.set("x", 1)
        assertEquals(7, filePrefs.int("x", 7))
    }
}
