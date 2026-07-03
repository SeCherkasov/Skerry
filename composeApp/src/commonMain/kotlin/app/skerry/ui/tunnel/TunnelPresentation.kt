package app.skerry.ui.tunnel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.skerry.shared.tunnel.TunnelDirection
import app.skerry.ui.design.D
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.ports_type_local
import app.skerry.ui.generated.resources.ports_type_local_display
import app.skerry.ui.generated.resources.ports_type_remote
import app.skerry.ui.generated.resources.ports_type_remote_display
import app.skerry.ui.generated.resources.ports_type_socks
import app.skerry.ui.generated.resources.ports_type_socks_display
import org.jetbrains.compose.resources.stringResource

/**
 * Представление типа туннеля (бейдж/подпись/цвета) — одна точка правды для desktop
 * ([TunnelsView]) и mobile (`MobilePortsView`), по образцу
 * [app.skerry.ui.forward.forwardTypeLabel].
 */

/** Метка бейджа типа туннеля: `-L`→LOCAL, `-R`→REMOTE, `-D`→SOCKS. */
@Composable
fun TunnelDirection.badgeLabel(): String = when (this) {
    TunnelDirection.Local -> stringResource(Res.string.ports_type_local)
    TunnelDirection.Remote -> stringResource(Res.string.ports_type_remote)
    TunnelDirection.Dynamic -> stringResource(Res.string.ports_type_socks)
}

/** Полная подпись типа для селекта: «Local forward (-L)» и т.д. */
@Composable
fun TunnelDirection.displayLabel(): String = when (this) {
    TunnelDirection.Local -> stringResource(Res.string.ports_type_local_display)
    TunnelDirection.Remote -> stringResource(Res.string.ports_type_remote_display)
    TunnelDirection.Dynamic -> stringResource(Res.string.ports_type_socks_display)
}

/** Цвета бейджа типа: фон (полупрозрачный акцент) + текст. */
fun TunnelDirection.badgeColors(): Pair<Color, Color> = when (this) {
    TunnelDirection.Local -> D.cyan.copy(alpha = 0.12f) to D.cyanBright
    TunnelDirection.Remote -> D.amber.copy(alpha = 0.14f) to D.amber
    TunnelDirection.Dynamic -> D.moss.copy(alpha = 0.14f) to D.moss
}
