package app.skerry.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.shared.ai.CommandRisk
import app.skerry.ui.ai.TerminalAiController
import app.skerry.ui.app.LocalAi
import app.skerry.ui.app.LocalFeatures
import app.skerry.ui.design.ChipButton
import app.skerry.ui.design.D
import app.skerry.ui.design.HLine
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.term_ai_ask_placeholder
import app.skerry.ui.generated.resources.term_ai_confirm_run
import app.skerry.ui.generated.resources.term_ai_dismiss
import app.skerry.ui.generated.resources.term_ai_run
import app.skerry.ui.generated.resources.term_ai_run_anyway
import app.skerry.ui.generated.resources.term_ai_thinking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.stringResource

// AI-бар терминального view: живой ввод под per-host политикой или декоративный мок-превью.

/**
 * Решает, показывать ли терминальный AI-бар и в каком режиме. Живой контроллер ([LocalAi]) → бар
 * работает под per-host политикой ([Host.aiPolicy]): [app.skerry.shared.ai.AiPolicy.Off] прячет бар
 * целиком. Без контроллера, но с фича-флагом → прежний декоративный мок (для дизайн-превью).
 */
@Composable
internal fun TerminalAiBarSlot() {
    // Достигается только вне живого пути (нет контроллера): декоративный AI-бар для дизайн-превью.
    if (LocalAi.current == null && LocalFeatures.current.ai) AiBarMock()
}

/**
 * Единственная форма AI-бара — постоянная высота, терминал над ней не ресайзится (нет «дёрга») и ничего
 * не перекрывается. В одной строке ВСЁ: ввод, «Thinking…», blocked/error, а для предложения — команда
 * + инлайн-пояснение (None: что делает; Warn/Danger: причина риска цветом) + кнопки. Деструктивная
 * команда красная с запрещающим знаком «block». Автозапуска нет (вывод недоверенный): «Run» =
 * подтверждение (команда + CR → в шелл); для [CommandRisk.Danger] нужен второй тап («Run anyway» → «Confirm run»).
 */
@Composable
internal fun AiBarInput(
    controller: TerminalAiController,
    terminal: TerminalScreenState?,
    focusRequests: SharedFlow<Unit> = MutableSharedFlow(),
) {
    val mono = LocalFonts.current.mono
    var prompt by remember { mutableStateOf("") }
    val submit = {
        val text = prompt.trim()
        if (text.isNotEmpty()) { controller.ask(text); prompt = "" }
    }
    // Хоткей ⌘/ / Ctrl+Shift+/ фокусирует строку ввода. requestFocus обёрнут в runCatching: если бар
    // сейчас показывает pending/thinking (поля ввода нет в композиции), FocusRequester не привязан —
    // запрос просто игнорируется, а не падает. SharedFlow не реплеится → переунтаж бара фокус не крадёт.
    val promptFocus = remember { FocusRequester() }
    LaunchedEffect(focusRequests) {
        focusRequests.collect {
            // «/» из аккорда роняет KEY_TYPED в только что сфокусированное поле (typed-события идут мимо
            // onPreviewKeyEvent, который мы погасили лишь на KeyDown). Фокус = чистый слейт: чистим до и
            // после короткого окна, чтобы стереть утёкший символ раньше, чем человек начнёт печатать.
            prompt = ""
            runCatching { promptFocus.requestFocus() }
            delay(50)
            prompt = ""
        }
    }
    val pending = controller.pending
    val risk = controller.pendingRisk?.risk ?: CommandRisk.None
    val danger = risk == CommandRisk.Danger
    // Красным красим ЛЮБУЮ деструктивную команду (удаление/перезапись), даже на уровне Warn.
    val severe = danger || controller.pendingRisk?.destructive == true
    val accent = if (severe) D.sunset else D.moss
    var armed by remember(pending) { mutableStateOf(false) }
    Column {
        HLine()
        Row(
            Modifier.fillMaxWidth()
                .background(if (pending != null) accent.copy(alpha = 0.08f) else D.surface2)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val leadColor = if (pending != null) accent else D.amber
            // Для деструктивной команды — запрещающий знак «block» вместо иконки терминала.
            val leadIcon = if (pending != null) (if (severe) "block" else "terminal") else "auto_awesome"
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(leadColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Sym(leadIcon, size = 16.sp, color = leadColor)
            }
            Box(Modifier.weight(1f)) {
                when {
                    pending != null -> {
                        // Одна строка: команда + инлайн-пояснение/причина риска (без отдельной плашки).
                        val infoColor = if (severe) D.sunset else if (risk == CommandRisk.Warn) D.amber else D.dim
                        val info = when (risk) {
                            CommandRisk.None -> controller.pendingInfo
                            else -> controller.pendingRisk?.reason
                        }
                        // Команда и пояснение — по общей базовой линии (разный кегль не «плавает»).
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Деструктивную команду подсвечиваем красным. Команда переносится (до 6 строк),
                            // а не обрезается многоточием: пользователь должен видеть ЦЕЛИКОМ то, что
                            // подтверждает и исполнит — иначе за «…» мог бы скрыться опасный хвост.
                            Txt(pending, color = if (severe) D.sunset else D.text, size = 13.sp, font = mono, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
                            if (info != null) Txt(info, color = infoColor, size = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).alignByBaseline())
                        }
                    }
                    controller.busy -> Txt(stringResource(Res.string.term_ai_thinking), color = D.dim, size = 13.sp)
                    controller.blocked != null -> Txt(controller.blocked!!, color = D.amber, size = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    controller.error != null -> Txt(controller.error!!, color = D.sunset, size = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else -> {
                        if (prompt.isEmpty()) Txt(stringResource(Res.string.term_ai_ask_placeholder), color = D.dim, size = 13.sp)
                        BasicTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            singleLine = true,
                            textStyle = TextStyle(color = D.text, fontSize = 13.sp),
                            cursorBrush = SolidColor(D.cyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(promptFocus),
                        )
                    }
                }
            }
            when {
                pending != null -> {
                    AiActionChip(
                        label = when { !danger -> stringResource(Res.string.term_ai_run); !armed -> stringResource(Res.string.term_ai_run_anyway); else -> stringResource(Res.string.term_ai_confirm_run) },
                        color = accent,
                        enabled = terminal != null,
                        onClick = {
                            if (danger && !armed) armed = true
                            else controller.confirm()?.let { terminal?.send(it + "\r") }
                        },
                    )
                    AiActionChip(stringResource(Res.string.term_ai_dismiss), D.faint, onClick = { controller.dismiss() })
                }
                controller.blocked != null || controller.error != null ->
                    AiActionChip(stringResource(Res.string.term_ai_dismiss), D.faint, onClick = { controller.dismiss() })
                else -> {
                    AiBarTag("verified_user", controller.policy.name.uppercase(), mono)
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(D.cyan)
                            .clickable(enabled = !controller.busy) { submit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym("arrow_upward", size = 16.sp, color = D.ink)
                    }
                }
            }
        }
    }
}

/** Компактная кнопка-чип в карточке предложения (Run/Dismiss) — залитая форма [ChipButton]. */
@Composable
private fun AiActionChip(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    ChipButton(label, color = color, onClick = onClick, enabled = enabled, filled = true, size = 12.sp, weight = FontWeight.Medium, verticalPadding = 5.dp)
}

/** Прежний декоративный AI-бар для чистого дизайн-превью (без живого контроллера). */
@Composable
private fun AiBarMock() {
    val mono = LocalFonts.current.mono
    Column {
        HLine()
        Row(
            Modifier.fillMaxWidth().background(D.surface2).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(D.amber.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Sym("auto_awesome", size = 16.sp, color = D.amber)
            }
            Txt("Ask Skerry: 'find files larger than 100MB'   ·   Ctrl / to focus", color = D.dim, size = 13.sp, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiBarTag("lock", "Local · Qwen 2.5", mono)
                AiBarTag("verified_user", "STRICT", mono)
            }
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(D.cyan), contentAlignment = Alignment.Center) {
                Sym("arrow_upward", size = 16.sp, color = D.ink)
            }
        }
    }
}

@Composable
private fun AiBarTag(icon: String, text: String, mono: FontFamily) {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, D.line, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Sym(icon, size = 12.sp, color = D.faint)
        Txt(text, color = D.faint, size = 10.5.sp, font = mono)
    }
}
