package app.skerry.sync.wire

import kotlinx.serialization.Serializable

/**
 * JSON-контракт «на проводе» клиент⇆sync-сервер (`docs/skerry-sync-design.md` §3) — единственный
 * источник для обеих сторон (`server` и `shared/sync`), ручного зеркала DTO больше нет. Шифроблобы
 * передаются как base64-строки (`blob`, `wrappedDataKey`, `encryptedDataKey`) — сервер их не
 * расшифровывает. Серверные admin-DTO (консоль видит только метаданные) остаются в
 * `server/.../model/Dto.kt`: клиент про них не знает.
 */

// --- auth ---

@Serializable
data class RegisterRequest(
    val accountId: String,
    val srpSalt: String,
    val srpVerifier: String,
    val wrappedDataKey: String,
    val deviceId: String,
    val deviceName: String,
    // Опционально (default null): старые клиенты без поля остаются совместимыми по wire.
    val platform: String? = null,
)

@Serializable
data class ChallengeRequest(val accountId: String)

@Serializable
data class ChallengeResponse(val challengeId: String, val salt: String, val b: String)

@Serializable
data class VerifyRequest(
    val challengeId: String,
    val a: String,
    val m1: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String? = null,
)

@Serializable
data class VerifyResponse(val m2: String, val accessToken: String, val refreshToken: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String)

// --- vault ---

@Serializable
data class KeysResponse(val wrappedDataKey: String)

@Serializable
data class RecordDto(
    val id: String,
    val type: String,
    val version: Long,
    val updatedAt: String,
    val deviceId: String,
    val deleted: Boolean,
    val blob: String,
)

/**
 * Дельта: записи + новый курсор синхронизации, который клиент сохраняет как `lastSyncVersion`.
 * [compactedIds] — id надгробий, полностью распространённых на все устройства (serverSeq ≤ watermark):
 * клиент по ним физически забывает тромбстоуны и перестаёт их пушить (иначе re-push воскрешал бы их
 * после purge). Поле с дефолтом — старый клиент его игнорирует.
 */
@Serializable
data class RecordsResponse(
    val records: List<RecordDto>,
    val cursor: Long,
    val compactedIds: List<String> = emptyList(),
)

@Serializable
data class PushRequest(val records: List<RecordDto>)

/** Победившее по LWW состояние каждой посланной записи + новый курсор. */
@Serializable
data class PushResponse(val records: List<RecordDto>, val cursor: Long)

// --- devices ---

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val revoked: Boolean,
    val current: Boolean,
)

@Serializable
data class DevicesResponse(val devices: List<DeviceDto>)

// --- pairing (вариант B) ---

@Serializable
data class PairingStartRequest(val encryptedDataKey: String, val ttlSeconds: Long? = null)

@Serializable
data class PairingStartResponse(val code: String, val expiresAt: Long)

@Serializable
data class PairingClaimRequest(val code: String, val deviceId: String, val deviceName: String)

@Serializable
data class PairingClaimResponse(
    val accountId: String,
    val encryptedDataKey: String,
    val accessToken: String,
    val refreshToken: String,
)
