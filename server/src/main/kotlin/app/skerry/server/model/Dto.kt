package app.skerry.server.model

import kotlinx.serialization.Serializable

/**
 * Серверные DTO: admin-консоль, статистика, ошибки. Wire-контракт клиент⇆сервер (auth/vault/
 * devices/pairing, `docs/skerry-sync-design.md` §3) живёт в модуле `:sync-wire`
 * (`app.skerry.sync.wire`) — единый источник для обеих сторон, ручного зеркала больше нет.
 */

/**
 * Устройство в админ-консоли: те же открытые метаданные, что и в [DeviceDto], плюс `accountId`
 * (консоль видит все аккаунты инстанса и отзывает по паре accountId+id — deviceId уникален лишь
 * в пределах аккаунта). Содержимого по-прежнему нет.
 */
@Serializable
data class AdminDeviceDto(
    val accountId: String,
    val id: String,
    val name: String,
    val platform: String?,
    val createdAt: Long,
    val lastSeenAt: Long,
    val syncVersion: Long?,
    val revoked: Boolean,
)

@Serializable
data class AdminDevicesResponse(val devices: List<AdminDeviceDto>, val total: Long)

/** Событие аудит-лога для консоли: только метаданные синхронизации, `createdAt` — epoch millis. */
@Serializable
data class AdminActivityDto(
    val accountId: String,
    val deviceId: String?,
    val event: String,
    val detail: String,
    val createdAt: Long,
)

@Serializable
data class AdminActivityResponse(val events: List<AdminActivityDto>, val total: Long)

/**
 * Аккаунт инстанса для консоли: открытые метаданные ([id] — он же email/identity) и агрегаты,
 * посчитанные на стороне БД. Содержимого записей здесь нет — только их число, число tombstone'ов
 * и суммарный размер шифроблобов. [lastSeenAt] — самая свежая активность любого устройства аккаунта.
 */
@Serializable
data class AdminAccountDto(
    val id: String,
    val createdAt: Long,
    val syncSeq: Long,
    val devices: Int,
    val activeDevices: Int,
    val records: Int,
    val tombstones: Int,
    val storageBytes: Long,
    val lastSeenAt: Long?,
)

@Serializable
data class AdminAccountsResponse(val accounts: List<AdminAccountDto>, val total: Long)

/**
 * Envelope записи vault, как её РЕАЛЬНО видит сервер: открытые метаданные синхронизации плюс размер
 * шифроблоба и [previewHex] — первые байты настоящего шифротекста (непрозрачный шум). Содержимого
 * нет по определению: без dataKey блоб нечитаем. Это честная замена прежней нарисованной карточки.
 */
@Serializable
data class AdminRecordDto(
    val id: String,
    val type: String,
    val version: Long,
    val updatedAt: String,
    val deviceId: String,
    val deleted: Boolean,
    val blobBytes: Int,
    val serverSeq: Long,
    val previewHex: String,
)

@Serializable
data class AdminRecordsResponse(val accountId: String, val records: List<AdminRecordDto>)

/** Результат purge tombstone'ов: сколько надгробий физически удалено (освобождено места). */
@Serializable
data class AdminPurgeResponse(val purged: Int)

// --- admin / errors ---

@Serializable
data class StatsResponse(
    val accounts: Long,
    val devices: Long,
    val records: Long,
    val pairingSessions: Long,
    val storageBytes: Long,
)

@Serializable
data class HealthResponse(val status: String, val version: String)

@Serializable
data class ErrorResponse(val error: String)
