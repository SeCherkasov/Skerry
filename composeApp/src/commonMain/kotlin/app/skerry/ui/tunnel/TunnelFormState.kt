package app.skerry.ui.tunnel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skerry.shared.tunnel.TunnelDirection

/**
 * Состояние формы создания/правки туннеля: редактируемые поля как Compose-state (по образцу
 * [app.skerry.ui.host.NewConnectionFormState]). Общее для desktop-редактора (`TunnelEditor` в
 * [TunnelsView]) и мобильного листа (`MobileTunnelEditorSheet`) — одна точка правды по seed'у
 * полей и сборке черновика, чтобы формы не разъезжались.
 *
 * Идентичность правимого туннеля ([editingId]) фиксируется при создании формы ([fromEntry]) и
 * уходит в [draft]; поля — изолированный буфер правки (мутации `entry.tunnel` в обход сюда
 * намеренно не долетают — приоритет у незавершённых правок пользователя, см. вызывающие вью:
 * `remember(editingId) { TunnelFormState.fromEntry(existing) }`).
 */
@Stable
class TunnelFormState private constructor(private val editingId: String?) {
    var label: String by mutableStateOf("")
    var direction: TunnelDirection by mutableStateOf(TunnelDirection.Local)
    var hostId: String? by mutableStateOf(null)
    var bindHost: String by mutableStateOf("127.0.0.1")
    var bindPort: String by mutableStateOf("")
    var destHost: String by mutableStateOf("")
    var destPort: String by mutableStateOf("")

    /** SOCKS (`-D`): назначения нет — форма прячет поля destination. */
    val isDynamic: Boolean get() = direction == TunnelDirection.Dynamic

    /** Валидный черновик для [TunnelManager.save] или `null`, пока ввод неполон (см. [buildTunnelDraft]). */
    val draft: TunnelDraft?
        get() = buildTunnelDraft(editingId, label, hostId, direction, bindHost, bindPort, destHost, destPort)

    companion object {
        /**
         * Форма, предзаполненная из [entry] (правка), либо пустая с дефолтами (создание,
         * `entry == null`): тип `-L`, bind-адрес loopback, порты пустые.
         */
        fun fromEntry(entry: TunnelEntry?): TunnelFormState {
            val seed = entry?.tunnel
            return TunnelFormState(entry?.id).apply {
                if (seed != null) {
                    label = seed.label
                    direction = seed.direction
                    hostId = seed.hostId
                    bindHost = seed.bindHost
                    bindPort = seed.bindPort.toString()
                    destHost = seed.destHost ?: ""
                    destPort = seed.destPort?.toString() ?: ""
                }
            }
        }
    }
}
