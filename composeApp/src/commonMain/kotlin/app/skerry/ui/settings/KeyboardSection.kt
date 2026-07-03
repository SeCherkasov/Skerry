package app.skerry.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.Badge
import app.skerry.ui.design.D
import app.skerry.ui.design.HLine
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.settings_badge_soon
import app.skerry.ui.generated.resources.settings_kb_accept_autocomplete
import app.skerry.ui.generated.resources.settings_kb_command_palette
import app.skerry.ui.generated.resources.settings_kb_copy_selection
import app.skerry.ui.generated.resources.settings_kb_cycle_suggestions
import app.skerry.ui.generated.resources.settings_kb_focus_ai
import app.skerry.ui.generated.resources.settings_kb_global
import app.skerry.ui.generated.resources.settings_kb_lock
import app.skerry.ui.generated.resources.settings_kb_new_connection
import app.skerry.ui.generated.resources.settings_kb_next_prev_tab
import app.skerry.ui.generated.resources.settings_kb_open_sftp
import app.skerry.ui.generated.resources.settings_kb_paste
import app.skerry.ui.generated.resources.settings_kb_search_history
import app.skerry.ui.generated.resources.settings_kb_select_tab_number
import app.skerry.ui.generated.resources.settings_kb_split_terminal
import app.skerry.ui.generated.resources.settings_kb_terminal_group
import app.skerry.ui.generated.resources.settings_keyboard_subtitle
import app.skerry.ui.generated.resources.settings_keyboard_title
import org.jetbrains.compose.resources.stringResource

// Секция Keyboard: справочник хоткеев (глобальных и терминальных).

@Composable
internal fun KeyboardSection() {
    SectionTitle(stringResource(Res.string.settings_keyboard_title), stringResource(Res.string.settings_keyboard_subtitle))
    // Подпись под платформу: ⌘/⌥ на macOS, Ctrl+Shift/Alt на Linux/Windows — ровно то, что распознаёт
    // matchDesktopShortcut. На Ctrl-пути требуется Shift, поэтому чистый Ctrl+буква (Ctrl+L очистка,
    // Ctrl+D EOF, Ctrl+C сигнал) остаётся терминалу.
    val mac = isApplePlatform()
    val mod: (String) -> String = { k -> if (mac) "⌘$k" else "Ctrl+Shift+$k" }
    // Терминальные аккорды — литеральные Ctrl/Shift/Tab (не app-модификатор): на macOS показываем
    // символами ⌃/⇧, иначе словами.
    val ctrl: (String) -> String = { k -> if (mac) "⌃$k" else "Ctrl+$k" }
    val ctrlShift: (String) -> String = { k -> if (mac) "⌃⇧$k" else "Ctrl+Shift+$k" }
    val shift: (String) -> String = { k -> if (mac) "⇧$k" else "Shift+$k" }

    val global = listOf(
        KeyboardBinding(stringResource(Res.string.settings_kb_new_connection), mod("N"), live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_command_palette), mod("K"), live = false),
        KeyboardBinding(stringResource(Res.string.settings_kb_split_terminal), mod("D"), live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_next_prev_tab), "${ctrl("Tab")} / ${ctrlShift("Tab")}", live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_select_tab_number), if (mac) "⌥1–9" else "Alt+1–9", live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_focus_ai), mod("/"), live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_open_sftp), mod("F"), live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_lock), mod("L"), live = true),
    )
    // Хоткеи внутри терминала (обрабатывает TerminalScreen): автодополнение fish-стиля и reverse-search
    // истории (Ctrl-R) + копипаст. Работают, пока сфокусирован терминал сессии.
    val terminal = listOf(
        KeyboardBinding(stringResource(Res.string.settings_kb_accept_autocomplete), "Tab", live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_cycle_suggestions), shift("Tab"), live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_search_history), ctrl("R"), live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_copy_selection), ctrlShift("C"), live = true),
        KeyboardBinding(stringResource(Res.string.settings_kb_paste), ctrlShift("V"), live = true),
    )

    val mono = LocalFonts.current.mono
    KeyboardGroupLabel(stringResource(Res.string.settings_kb_global), top = 4.dp)
    global.forEach { KeyboardRow(it, mono) }
    KeyboardGroupLabel(stringResource(Res.string.settings_kb_terminal_group), top = 18.dp)
    terminal.forEach { KeyboardRow(it, mono) }
}

@Composable
private fun KeyboardGroupLabel(text: String, top: Dp) {
    Txt(text, color = D.faint, size = 10.sp, weight = FontWeight.SemiBold, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = top, bottom = 8.dp))
}

@Composable
private fun KeyboardRow(b: KeyboardBinding, mono: FontFamily) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(b.label, color = if (b.live) D.textBright else D.dim, size = 12.5.sp)
            // «Command palette» ещё нет как фичи — честно помечаем биндинг как будущий, а не молча
            // показываем нерабочий аккорд наравне с живыми.
            if (!b.live) Badge(stringResource(Res.string.settings_badge_soon), bg = Color(0x1AF2A65A), fg = D.amber, radius = 3, size = 9.sp)
        }
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x0AFFFFFF)).border(1.dp, D.cyan14, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Txt(b.binding, color = D.dim, size = 11.sp, font = mono)
        }
    }
    HLine()
}

/** Строка страницы Keyboard: подпись, аккорд и признак «уже работает» (иначе — метка SOON). */
private data class KeyboardBinding(val label: String, val binding: String, val live: Boolean)
