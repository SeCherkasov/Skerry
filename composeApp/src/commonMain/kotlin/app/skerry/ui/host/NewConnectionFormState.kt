package app.skerry.ui.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Состояние формы «New connection» (модалка дизайн-слоя): редактируемые поля профиля как
 * Compose-state. Идентичность ([Host.id]) присваивает [HostManagerController] на сохранении,
 * поэтому форма оперирует черновиком и отдаёт [HostDraft] через [toDraft].
 *
 * Валидация ([canSave]) и парсинг порта — здесь (чистая логика, без рендера), зафиксированы
 * [app.skerry.ui.host.NewConnectionFormStateTest]; UI лишь связывает поля и кнопку Save.
 * Секрет/identity, AI-политика и теги в черновик пока не входят (отдельные слайсы) — форма
 * сохраняет базовый профиль (label/address/port/username/group).
 */
@Stable
class NewConnectionFormState {
    var name: String by mutableStateOf("")
    var address: String by mutableStateOf("")
    var port: String by mutableStateOf("22")
    var username: String by mutableStateOf("")
    var group: String by mutableStateOf("")

    /** Порт как валидное число в диапазоне TCP-портов, иначе `null`. */
    val portOrNull: Int? get() = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /** Можно ли сохранять: имя/адрес/пользователь не пусты и порт валиден. */
    val canSave: Boolean
        get() = name.isNotBlank() && address.isNotBlank() && username.isNotBlank() && portOrNull != null

    /** Собрать черновик для [HostManagerController.save]; [id] != null — правка существующего. */
    fun toDraft(id: String? = null): HostDraft = HostDraft(
        id = id,
        label = name.trim(),
        address = address.trim(),
        port = portOrNull ?: 22,
        username = username.trim(),
        group = group.trim().ifBlank { null },
    )
}
