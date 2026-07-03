package app.skerry.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.AnchoredDropdown
import app.skerry.ui.design.D
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt

/**
 * Мобильный редактор тегов с type-ahead: пилюли `#tag` с крестиком + инлайн-ввод нового тега
 * (Enter/запятая фиксирует пилюлю — логика фиксации у вызывающего через [onDraftChange]/[onCommit]).
 * При фокусе поля под ним раскрывается список [suggestions]; тап по подсказке вызывает [onPick].
 * Меню через [AnchoredDropdown] с `focusable = false`, чтобы не отнимать фокус и не прерывать набор.
 *
 * Общий для листов New connection (теги хоста) и Snippets (теги сниппета): параметризованы только
 * источник подсказок, плейсхолдер и фон меню ([menuBackground] — листы исторически используют
 * SheetPanel и D.surface2 соответственно; пиксели каждого места сохранены).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MobileTagsEditor(
    tags: List<String>,
    onRemove: (String) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    suggestions: List<String>,
    placeholder: String,
    onPick: (String) -> Unit,
    menuBackground: Color,
) {
    val fonts = LocalFonts.current
    val textStyle = remember(fonts.ui) { TextStyle(color = D.text, fontSize = 14.sp, fontFamily = fonts.ui) }
    var focused by remember { mutableStateOf(false) }
    AnchoredDropdown(
        expanded = focused && suggestions.isNotEmpty(),
        onDismiss = { focused = false },
        focusable = false, // не красть фокус у поля ввода тега
        trigger = {
            FlowRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(D.bg).border(1.dp, D.cyan14, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                tags.forEach { tag ->
                    key(tag) {
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(D.cyan.copy(alpha = 0.12f)).padding(start = 10.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Txt("#$tag", color = D.cyanBright, size = 12.5.sp)
                            Box(
                                Modifier.clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onRemove(tag) }.padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Sym("close", size = 14.sp, color = D.cyanBright)
                            }
                        }
                    }
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(D.cyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommit() }),
                    modifier = Modifier.widthIn(min = 90.dp).onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) Txt(placeholder, color = D.faint, size = 14.sp)
                            inner()
                        }
                    },
                )
            }
        },
        menu = { width ->
            Column(
                Modifier
                    .width(width)
                    .clip(RoundedCornerShape(11.dp))
                    .background(menuBackground)
                    .border(1.dp, D.cyan14, RoundedCornerShape(11.dp))
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                // Тап добавляет тег; фокус остаётся на поле — меню пересчитается без только что добавленного.
                suggestions.forEach { tag ->
                    key(tag) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(tag) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        ) {
                            Txt("#$tag", color = D.cyanBright, size = 14.sp)
                        }
                    }
                }
            }
        },
    )
}
