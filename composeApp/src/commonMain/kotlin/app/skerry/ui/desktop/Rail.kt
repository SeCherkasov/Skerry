package app.skerry.ui.desktop

import app.skerry.ui.app.DesktopView
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.rail_hosts
import app.skerry.ui.generated.resources.rail_snippets
import app.skerry.ui.generated.resources.rail_team
import app.skerry.ui.generated.resources.rail_terminal
import app.skerry.ui.generated.resources.rail_tunnels
import app.skerry.ui.generated.resources.rail_vault
import org.jetbrains.compose.resources.StringResource

/** Пункт левого icon-rail desktop-оболочки. Подпись — ресурс, локализуется по языку интерфейса. */
data class RailItem(val view: DesktopView, val icon: String, val label: StringResource)

// Files намеренно нет в рейле: SFTP открывается быстрой кнопкой (иконка folder) прямо на терминале
// активной сессии — отдельный пункт рейла дублировал бы её. [DesktopView.Sftp] остаётся session-вью.
val RAIL = listOf(
    RailItem(DesktopView.Terminal, "terminal", Res.string.rail_terminal),
    RailItem(DesktopView.Ports, "lan", Res.string.rail_tunnels),
    RailItem(DesktopView.Snippets, "code_blocks", Res.string.rail_snippets),
    RailItem(DesktopView.Vault, "vpn_key", Res.string.rail_vault),
    RailItem(DesktopView.Known, "fingerprint", Res.string.rail_hosts),
    RailItem(DesktopView.Teams, "groups", Res.string.rail_team),
)
