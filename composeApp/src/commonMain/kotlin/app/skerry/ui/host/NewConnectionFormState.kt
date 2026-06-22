package app.skerry.ui.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skerry.ui.identity.IdentityDraft
import app.skerry.ui.identity.IdentityKind

/**
 * Способ аутентификации, выбранный в форме «New connection».
 * - [ASK] — секрет не хранить, пароль спрашивается при каждом подключении (хост без identity);
 * - [EXISTING] — привязать уже сохранённую в vault identity ([existingIdentityId]);
 * - [NEW_PASSWORD] / [NEW_KEY] — создать новую identity (пароль / приватный ключ) в vault и привязать.
 */
enum class AuthMode { ASK, EXISTING, NEW_PASSWORD, NEW_KEY }

/**
 * Состояние формы «New connection» (модалка дизайн-слоя): редактируемые поля профиля как
 * Compose-state. Идентичность ([Host.id]) присваивает [HostManagerController] на сохранении,
 * поэтому форма оперирует черновиком и отдаёт [HostDraft] через [toDraft].
 *
 * Валидация ([canSave]) и парсинг порта/секрета — здесь (чистая логика, без рендера), зафиксированы
 * [app.skerry.ui.host.NewConnectionFormStateTest]; UI лишь связывает поля и кнопку Save.
 *
 * Аутентификация ([authMode]) разворачивается в идентификатор vault-записи через [resolveIdentityId]:
 * для новых секретов форма не пишет в vault сама, а вызывает переданный `saveIdentity` (обычно
 * [app.skerry.ui.identity.IdentityManagerController.save]) — побочный эффект остаётся снаружи, логика
 * выбора тестируема. AI-политика и теги в черновик пока не входят (отдельные слайсы).
 */
@Stable
class NewConnectionFormState {
    var name: String by mutableStateOf("")
    var address: String by mutableStateOf("")
    var port: String by mutableStateOf("22")
    var username: String by mutableStateOf("")
    var group: String by mutableStateOf("")

    // Аутентификация: режим + поля под каждый вид (держатся рядом, чтобы переключение не теряло ввод).
    var authMode: AuthMode by mutableStateOf(AuthMode.ASK)
    var existingIdentityId: String? by mutableStateOf(null)
    var password: String by mutableStateOf("")
    var privateKeyPem: String by mutableStateOf("")
    var passphrase: String by mutableStateOf("")

    /** Порт как валидное число в диапазоне TCP-портов, иначе `null`. */
    val portOrNull: Int? get() = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /** Заполнен ли выбранный способ аутентификации (для [canSave]). */
    private val authValid: Boolean
        get() = when (authMode) {
            AuthMode.ASK -> true
            AuthMode.EXISTING -> existingIdentityId != null
            AuthMode.NEW_PASSWORD -> password.isNotEmpty()
            AuthMode.NEW_KEY -> privateKeyPem.isNotBlank()
        }

    /** Можно ли сохранять: имя/адрес/пользователь не пусты, порт валиден и аутентификация заполнена. */
    val canSave: Boolean
        get() = name.isNotBlank() && address.isNotBlank() && username.isNotBlank() && portOrNull != null && authValid

    /** Метка автосоздаваемой identity — `user@address`, чтобы её было видно во вкладке Vault. */
    private fun identityLabel(): String = "${username.trim()}@${address.trim()}"

    /**
     * Разрешить [Host.identityId] для черновика: для [AuthMode.EXISTING] — выбранный id, для новых
     * секретов — id созданной через [saveIdentity] записи, для [AuthMode.ASK] — `null` (секрет не
     * хранится). [saveIdentity] вызывается ровно для новых секретов (создаёт запись в vault).
     */
    fun resolveIdentityId(saveIdentity: (IdentityDraft) -> String?): String? = when (authMode) {
        AuthMode.ASK -> null
        AuthMode.EXISTING -> existingIdentityId
        AuthMode.NEW_PASSWORD -> saveIdentity(
            IdentityDraft(label = identityLabel(), kind = IdentityKind.PASSWORD, password = password),
        )
        AuthMode.NEW_KEY -> saveIdentity(
            IdentityDraft(
                label = identityLabel(),
                kind = IdentityKind.PRIVATE_KEY,
                privateKeyPem = privateKeyPem,
                passphrase = passphrase,
            ),
        )
    }

    /** Собрать черновик для [HostManagerController.save]; [id] != null — правка существующего. */
    fun toDraft(id: String? = null, identityId: String? = null): HostDraft = HostDraft(
        id = id,
        label = name.trim(),
        address = address.trim(),
        port = portOrNull ?: 22,
        username = username.trim(),
        group = group.trim().ifBlank { null },
        identityId = identityId,
    )
}
