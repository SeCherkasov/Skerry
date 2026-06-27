package app.skerry.ui.tunnel

import app.skerry.shared.host.Host
import app.skerry.shared.tunnel.TunnelDirection
import app.skerry.ui.connection.toSshAuth
import app.skerry.ui.connection.toTarget
import app.skerry.shared.vault.Credential

// Чистые хелперы редактора туннелей — вынесены из Compose, чтобы покрываться тестами без UI.

private fun parsePort(value: String, min: Int): Int? =
    value.trim().toIntOrNull()?.takeIf { it in min..65535 }

/**
 * Собрать валидный [TunnelDraft] из полей формы или `null`, если ввод неполон/некорректен. Правила:
 * имя и хост обязательны; bind-порт `0..65535` (0 = выберет ОС); для `-L`/`-R` обязателен адрес и порт
 * назначения (`1..65535`); для `-D` (SOCKS) назначения нет. Пустой bind-адрес → loopback.
 */
fun buildTunnelDraft(
    id: String?,
    label: String,
    hostId: String?,
    direction: TunnelDirection,
    bindHost: String,
    bindPort: String,
    destHost: String,
    destPort: String,
): TunnelDraft? {
    val name = label.trim().ifEmpty { return null }
    val host = hostId?.takeIf { it.isNotBlank() } ?: return null
    val bind = parsePort(bindPort, min = 0) ?: return null
    val resolvedBindHost = bindHost.trim().ifEmpty { "127.0.0.1" }
    return when (direction) {
        TunnelDirection.Dynamic -> TunnelDraft(
            id = id, label = name, hostId = host, direction = direction,
            bindHost = resolvedBindHost, bindPort = bind, destHost = null, destPort = null,
        )
        TunnelDirection.Local, TunnelDirection.Remote -> {
            val dHost = destHost.trim().ifEmpty { return null }
            val dPort = parsePort(destPort, min = 1) ?: return null
            TunnelDraft(
                id = id, label = name, hostId = host, direction = direction,
                bindHost = resolvedBindHost, bindPort = bind, destHost = dHost, destPort = dPort,
            )
        }
    }
}

/**
 * Резолв сохранённого туннеля к параметрам подключения (для production-лямбды [TunnelManager]).
 * Хост ищется по [Tunnel.hostId], секрет — по [Host.credentialId] в открытом vault. Сообщения —
 * generic-константы (без технических деталей), так требует контракт [TunnelResolution.Unavailable].
 */
fun resolveTunnel(
    tunnel: app.skerry.shared.tunnel.Tunnel,
    findHost: (String) -> Host?,
    findCredential: (String?) -> Credential?,
): TunnelResolution {
    val host = findHost(tunnel.hostId) ?: return TunnelResolution.Unavailable("Host not found")
    val credential = findCredential(host.credentialId)
        ?: return TunnelResolution.Unavailable("No saved credential")
    return TunnelResolution.Ready(host.toTarget(), credential.toSshAuth())
}
