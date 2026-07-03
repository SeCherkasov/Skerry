package app.skerry.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.app.DesktopDesignState
import app.skerry.ui.design.D
import app.skerry.ui.design.DropdownField
import app.skerry.ui.design.HLine
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.settings_terminal_cursor_bar_blink
import app.skerry.ui.generated.resources.settings_terminal_cursor_bar_steady
import app.skerry.ui.generated.resources.settings_terminal_cursor_block_blink
import app.skerry.ui.generated.resources.settings_terminal_cursor_block_steady
import app.skerry.ui.generated.resources.settings_terminal_cursor_style
import app.skerry.ui.generated.resources.settings_terminal_cursor_underline_blink
import app.skerry.ui.generated.resources.settings_terminal_cursor_underline_steady
import app.skerry.ui.generated.resources.settings_terminal_scrollback
import app.skerry.ui.generated.resources.settings_terminal_scrollback_desc
import app.skerry.ui.generated.resources.settings_terminal_show_title
import app.skerry.ui.generated.resources.settings_terminal_show_title_desc
import app.skerry.ui.generated.resources.settings_terminal_subtitle
import app.skerry.ui.generated.resources.settings_terminal_title
import app.skerry.ui.terminal.TERMINAL_SCROLLBACK_OPTIONS
import app.skerry.ui.terminal.TerminalCursorStyle
import org.jetbrains.compose.resources.stringResource

// Секция Terminal: scrollback, стиль курсора, живой OSC-заголовок на вкладках.

@Composable
internal fun TerminalSection(state: DesktopDesignState) {
    SectionTitle(stringResource(Res.string.settings_terminal_title), stringResource(Res.string.settings_terminal_subtitle))
    // Буфер прокрутки: глубина scrollback новой сессии (селект пресетов справа от подписи).
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(stringResource(Res.string.settings_terminal_scrollback), color = D.text, size = 13.sp, weight = FontWeight.Medium)
            Txt(stringResource(Res.string.settings_terminal_scrollback_desc), color = D.dim, size = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Box(Modifier.width(160.dp)) { ScrollbackPicker(state.terminalScrollback, onPick = state::chooseTerminalScrollback) }
    }
    HLine()
    // Стиль курсора: форма × мигание по умолчанию для новой сессии.
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Txt(stringResource(Res.string.settings_terminal_cursor_style), color = D.text, size = 13.sp, weight = FontWeight.Medium) }
        Box(Modifier.width(200.dp)) { CursorStylePicker(state.terminalCursorStyle, onPick = state::chooseTerminalCursorStyle) }
    }
    HLine()
    // Живой OSC-заголовок терминала на вкладках: включает ветку effectiveTabTitle в Session.tabTitle.
    SettingToggleRow(
        stringResource(Res.string.settings_terminal_show_title),
        stringResource(Res.string.settings_terminal_show_title_desc),
        on = state.showTerminalTitleOnTabs,
        onToggle = state::toggleShowTerminalTitleOnTabs,
    )
}

/** Локализованная подпись стиля курсора (форма + мигание) для дропдауна и триггера. */
@Composable
private fun TerminalCursorStyle.label(): String = stringResource(
    when (this) {
        TerminalCursorStyle.BlockBlink -> Res.string.settings_terminal_cursor_block_blink
        TerminalCursorStyle.BlockSteady -> Res.string.settings_terminal_cursor_block_steady
        TerminalCursorStyle.UnderlineBlink -> Res.string.settings_terminal_cursor_underline_blink
        TerminalCursorStyle.UnderlineSteady -> Res.string.settings_terminal_cursor_underline_steady
        TerminalCursorStyle.BarBlink -> Res.string.settings_terminal_cursor_bar_blink
        TerminalCursorStyle.BarSteady -> Res.string.settings_terminal_cursor_bar_steady
    },
)

/** Выпадающий список глубины scrollback ([TERMINAL_SCROLLBACK_OPTIONS], строк; формат «10 000»). */
@Composable
private fun ScrollbackPicker(current: Int, onPick: (Int) -> Unit) {
    DropdownField(current, TERMINAL_SCROLLBACK_OPTIONS, label = { formatScrollback(it) }, onPick = onPick)
}

/** Выпадающий список стиля курсора ([TerminalCursorStyle.entries]). */
@Composable
private fun CursorStylePicker(current: TerminalCursorStyle, onPick: (TerminalCursorStyle) -> Unit) {
    DropdownField(current, TerminalCursorStyle.entries, label = { it.label() }, onPick = onPick)
}

/** «10000» → «10 000» (неразрывный пробел между тысячами) для читаемости счётчика строк. */
private fun formatScrollback(lines: Int): String =
    lines.toString().reversed().chunked(3).joinToString(" ").reversed()
