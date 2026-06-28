package app.skerry.ui.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalAppearanceTest {

    @Test
    fun default_is_hack() {
        assertEquals(TerminalFont.Hack, TerminalFont.DEFAULT)
    }

    @Test
    fun fromId_round_trips_every_font() {
        TerminalFont.entries.forEach { font ->
            assertEquals(font, TerminalFont.fromId(font.id))
        }
    }

    @Test
    fun fromId_falls_back_to_default_for_unknown_or_null() {
        assertEquals(TerminalFont.DEFAULT, TerminalFont.fromId("nope"))
        assertEquals(TerminalFont.DEFAULT, TerminalFont.fromId(null))
        assertEquals(TerminalFont.DEFAULT, TerminalFont.fromId(""))
    }

    @Test
    fun default_size_is_within_allowed_sizes() {
        assertTrue(DEFAULT_TERMINAL_FONT_SIZE in TERMINAL_FONT_SIZES)
    }

    @Test
    fun appearance_defaults_match_constants() {
        val a = TerminalAppearance()
        assertEquals(TerminalFont.DEFAULT, a.font)
        assertEquals(DEFAULT_TERMINAL_FONT_SIZE, a.fontSizeSp)
    }
}
