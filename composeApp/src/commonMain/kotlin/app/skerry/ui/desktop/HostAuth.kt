package app.skerry.ui.desktop

import app.skerry.shared.host.Host
import app.skerry.shared.ssh.ConnectionType
import app.skerry.shared.ssh.SshAuth
import app.skerry.ui.connection.toSshAuth
import app.skerry.ui.identity.CredentialManagerController

/**
 * Результат резолва аутентификации хоста перед подключением: либо готовый [SshAuth], либо
 * SSH-хост без привязанного keychain-секрета — UI должен спросить пароль у пользователя.
 */
sealed interface HostAuthResolution {
    /** Аутентификация определена без участия пользователя. */
    data class Resolved(val auth: SshAuth) : HostAuthResolution

    /** SSH-хост без привязанного секрета — перед подключением нужен ввод пароля. */
    data object NeedsPassword : HostAuthResolution
}

/**
 * Одноуровневый резолв «хост → способ аутентификации», общий для подключения в новую вкладку,
 * split-панель и «Run snippet on host»: Telnet/Serial аутентификации не требуют (auth игнорируется —
 * пустой пароль-заглушка); SSH-хост с привязанным секретом → [app.skerry.shared.vault.Credential] из
 * keychain разворачивается в [SshAuth]; SSH-хост без привязки → [HostAuthResolution.NeedsPassword].
 */
fun resolveHostAuth(host: Host, credentials: CredentialManagerController?): HostAuthResolution = when {
    // Telnet/Serial без аутентификации — коннектим сразу, без запроса пароля (auth игнорируется).
    host.connectionType != ConnectionType.SSH -> HostAuthResolution.Resolved(SshAuth.Password(""))
    else ->
        credentials?.find(host.credentialId)?.let { HostAuthResolution.Resolved(it.toSshAuth()) }
            ?: HostAuthResolution.NeedsPassword
}
