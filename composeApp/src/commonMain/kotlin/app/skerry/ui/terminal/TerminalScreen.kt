package app.skerry.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.shared.terminal.TerminalState
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.jetbrainsmono_bold
import app.skerry.ui.generated.resources.jetbrainsmono_regular
import app.skerry.ui.theme.SkerryColors
import org.jetbrains.compose.resources.Font

/**
 * Моноширинное семейство Skerry — JetBrains Mono из compose-resources.
 * `Font(...)` сам @Composable и кэширует ресурс внутри, поэтому remember не нужен.
 */
@Composable
fun rememberJetBrainsMono(): FontFamily = FontFamily(
    Font(Res.font.jetbrainsmono_regular, weight = FontWeight.Normal),
    Font(Res.font.jetbrainsmono_bold, weight = FontWeight.Bold),
)

/**
 * Минимальный терминальный экран: моноширинный вывод PTY на фоне [SkerryColors.terminalBg]
 * и строка ввода. Ввод пока построчный — текст уходит в PTY по Enter; посимвольный (raw)
 * режим и интерпретация ANSI/VT — следующие шаги.
 */
@Composable
fun TerminalScreen(state: TerminalScreenState, modifier: Modifier = Modifier) {
    val mono = rememberJetBrainsMono()
    val textStyle = remember(mono) {
        TextStyle(
            fontFamily = mono,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = SkerryColors.text,
        )
    }
    val sessionState by state.state.collectAsState()
    val closed = sessionState == TerminalState.Closed
    val scroll = rememberScrollState()
    var input by remember { mutableStateOf("") }

    // Автоскролл вниз по мере нового вывода (буфер монотонно растёт — длины достаточно).
    LaunchedEffect(state.output.length) { scroll.scrollTo(scroll.maxValue) }

    Column(modifier.fillMaxSize().background(SkerryColors.terminalBg)) {
        Text(
            text = state.output,
            style = textStyle,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SkerryColors.nightSea)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (closed) "× " else "❯ ",
                style = textStyle.copy(color = if (closed) SkerryColors.storm else SkerryColors.cyan),
            )
            val onSubmit: () -> Unit = { submit(state, input); input = "" }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !closed,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(SkerryColors.cyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { onSubmit() },
                    onDone = { onSubmit() },
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun submit(state: TerminalScreenState, line: String) {
    state.send(line + "\n")
}
