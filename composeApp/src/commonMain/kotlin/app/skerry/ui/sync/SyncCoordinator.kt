package app.skerry.ui.sync

import app.skerry.shared.sync.DeviceInfo
import app.skerry.shared.sync.RemoteDevice
import app.skerry.shared.sync.SyncClient
import app.skerry.shared.sync.SyncEngine
import app.skerry.shared.sync.SyncException
import app.skerry.shared.sync.SyncSession
import app.skerry.shared.sync.SyncStateStore
import app.skerry.shared.sync.InMemorySyncStateStore
import app.skerry.shared.vault.Vault
import app.skerry.shared.vault.VaultCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** Где приложение хранит настройку sync (URL сервера, accountId, deviceId) между запусками. */
interface SyncConfigStore {
    fun load(): SyncConfig?
    fun save(config: SyncConfig)
    fun clear()
}

/** Сохранённая привязка к серверу. Токены не храним в открытом виде — переавторизация по паролю. */
data class SyncConfig(val serverUrl: String, val accountId: String, val deviceId: String)

class InMemorySyncConfigStore : SyncConfigStore {
    private var config: SyncConfig? = null
    override fun load(): SyncConfig? = config
    override fun save(config: SyncConfig) { this.config = config }
    override fun clear() { config = null }
}

/** Видимое UI состояние подключения к sync. */
sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data object Busy : SyncStatus
    data class Online(val accountId: String, val lastPushed: Int, val lastPulled: Int) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * App-level склейка self-hosted sync (`docs/skerry-sync-design.md`): связывает [SyncClient],
 * [VaultCrypto] и локальный [Vault] в операции register/login/sync для UI. Zero-knowledge —
 * мастер-пароль и dataKey не покидают устройство; на сервер уходят SRP-верификатор и шифроблобы.
 *
 * Соль деривации masterKey выводится из accountId ([VaultCrypto.deriveSyncSalt]) — design §1,
 * чтобы другое устройство могло войти одним мастер-паролем. Требует разблокированного vault
 * (нужен dataKey для серверной обёртки). [clientFactory] строит сетевой клиент под URL —
 * платформенная реализация (KtorSyncClient на JVM/Android).
 */
class SyncCoordinator(
    private val clientFactory: (serverUrl: String) -> SyncClient,
    private val crypto: VaultCrypto,
    private val vault: Vault,
    private val configStore: SyncConfigStore = InMemorySyncConfigStore(),
    private val syncState: SyncStateStore = InMemorySyncStateStore(),
    private val deviceIdProvider: () -> String = { randomDeviceId() },
    private val deviceName: String = "Skerry device",
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var client: SyncClient? = null
    private var session: SyncSession? = null

    // Собственный scope: сетевые операции НЕ должны зависеть от жизненного цикла composable.
    // На мобильном форма перерисовывается по [status]: как только connect() ставит Busy, форма
    // покидает композицию — и если бы запуск шёл от её rememberCoroutineScope, операция отменилась бы
    // на полпути («rememberCoroutineScope left the composition»). Запуск здесь это исключает; Argon2id
    // (тяжёлый) тоже уходит с main-потока на Dispatchers.Default.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isConfigured: Boolean get() = configStore.load() != null

    /** Регистрация нового аккаунта на сервере с текущим (разблокированным) vault. Запуск fire-and-forget — прогресс/итог через [status]. */
    fun register(serverUrl: String, accountId: String, masterPassword: CharArray) {
        scope.launch { connect(serverUrl, accountId, masterPassword, registering = true) }
    }

    /** Вход в существующий аккаунт мастер-паролем. Запуск fire-and-forget — прогресс/итог через [status]. */
    fun login(serverUrl: String, accountId: String, masterPassword: CharArray) {
        scope.launch { connect(serverUrl, accountId, masterPassword, registering = false) }
    }

    private suspend fun connect(serverUrl: String, accountId: String, masterPassword: CharArray, registering: Boolean) {
        _status.value = SyncStatus.Busy
        val dataKey = vault.exportDataKey()
        if (dataKey == null) {
            _status.value = SyncStatus.Failed("vault is locked")
            masterPassword.fill(' ')
            return
        }
        try {
            val salt = crypto.deriveSyncSalt(accountId)
            val masterKey = crypto.deriveMasterKey(masterPassword, salt)
            val authKey = crypto.deriveAuthKey(masterKey)
            val deviceId = configStore.load()?.takeIf { it.accountId == accountId }?.deviceId ?: deviceIdProvider()
            val device = DeviceInfo(deviceId, deviceName)
            val syncClient = clientFactory(serverUrl)

            val newSession = if (registering) {
                val wrapped = crypto.wrapDataKey(masterKey, dataKey)
                syncClient.register(accountId, authKey, wrapped, device)
            } else {
                syncClient.login(accountId, authKey, device)
            }

            client = syncClient
            session = newSession
            configStore.save(SyncConfig(serverUrl, accountId, deviceId))
            runSync()
        } catch (e: SyncException) {
            _status.value = SyncStatus.Failed(syncErrorMessage(e))
        } finally {
            // Затирание байтов masterKey/dataKey — ответственность кода shared (их `bytes` internal);
            // здесь лишь не удерживаем ссылки дольше необходимого и чистим сам пароль.
            masterPassword.fill(' ')
        }
    }

    /** Прогнать один цикл синхронизации (pull/merge/push). No-op, если не подключены. */
    fun syncNow() {
        if (client == null || session == null) return
        scope.launch {
            _status.value = SyncStatus.Busy
            runSync()
        }
    }

    private suspend fun runSync() {
        val c = client ?: return
        val s = session ?: return
        try {
            val outcome = SyncEngine(c, vault, syncState).sync(s)
            _status.value = SyncStatus.Online(s.accountId, outcome.pushed, outcome.pulled)
        } catch (e: SyncException) {
            _status.value = SyncStatus.Failed(syncErrorMessage(e))
        }
    }

    suspend fun listDevices(): List<RemoteDevice> {
        val c = client ?: return emptyList()
        val s = session ?: return emptyList()
        // НЕ runCatching: он поглотил бы CancellationException и порвал structured concurrency.
        return try {
            c.listDevices(s)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Отключить sync на этом устройстве: забыть сессию и сохранённую привязку. */
    fun disconnect() {
        scope.launch {
            client?.close()
            client = null
            session = null
            configStore.clear()
            _status.value = SyncStatus.Disabled
        }
    }

    private fun syncErrorMessage(e: SyncException): String = when (e.kind) {
        SyncException.Kind.UNAUTHORIZED -> "Неверный мастер-пароль или аккаунт"
        SyncException.Kind.NOT_FOUND -> "Аккаунт не найден"
        SyncException.Kind.CONFLICT -> "Аккаунт уже существует"
        SyncException.Kind.GONE -> "Код паринга истёк"
        SyncException.Kind.NETWORK -> "Нет связи с сервером: ${e.message}"
        SyncException.Kind.PROTOCOL -> "Ошибка протокола синхронизации: ${e.message}"
    }
}

/** Случайный 128-битный deviceId как hex — без платформенных API (общий commonMain). */
private fun randomDeviceId(): String =
    (0 until 16).joinToString("") { "0123456789abcdef"[Random.nextInt(16)].toString() }
