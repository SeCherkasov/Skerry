package app.skerry.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.D
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt

// Общий chrome мобильных экранов: шапка push-экрана, заголовок корневого таба, FAB.

/**
 * Шапка push-экрана: chevron_left (назад) + заголовок 18sp Bold. [plainBack] = true гасит
 * indication на стрелке (interactionSource + indication = null) — исторический вариант части
 * экранов (Ports/Known/HostDetail); false — обычный clickable. Параметр сохраняет прежнее
 * поведение каждого места без визуальных изменений.
 */
@Composable
internal fun MobilePushHeader(title: String, onBack: () -> Unit, plainBack: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val backModifier = if (plainBack) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            )
        } else {
            Modifier.clickable(onClick = onBack)
        }
        Sym("chevron_left", size = 27.sp, color = D.cyanBright, modifier = backModifier)
        Txt(title, color = D.text, size = 18.sp, weight = FontWeight.Bold)
    }
}

/** Заголовок корневого таба (28sp Bold, letterSpacing −0.5) — Hosts/Snippets/Vault/More/Files. */
@Composable
internal fun MobileScreenTitle(text: String) {
    Txt(text, color = D.text, size = 28.sp, weight = FontWeight.Bold, letterSpacing = (-0.5).sp)
}

/**
 * Круглая FAB мобильных табов (cyan 56dp, радиус 18, тёмная иконка). [onClick] == null — инертная
 * (мок-путь превью). [icon]/[iconSize] параметризованы: Files переключает «+»/«×» и рисует иконку
 * 26sp, Hosts/Snippets — «+» 28sp (исторические метрики мест сохранены).
 */
@Composable
internal fun MobileFabButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: String = "add",
    iconSize: TextUnit = 28.sp,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(D.cyan)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        Sym(icon, size = iconSize, color = D.ink)
    }
}
