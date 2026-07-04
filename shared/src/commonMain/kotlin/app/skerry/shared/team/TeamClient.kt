package app.skerry.shared.team

import app.skerry.shared.sync.RecordPage
import app.skerry.shared.sync.RemoteRecord
import app.skerry.shared.sync.SyncSession

/**
 * Роль в команде (иерархия OWNER > ADMIN > EDITOR > VIEWER). Гейтит запись/управление, НЕ чтение —
 * у любого активного участника есть teamKey. Незнакомая строка деградирует в [VIEWER] (least
 * privilege); legacy `member` (до гранулярных ролей мог писать записи) читается как [EDITOR].
 */
enum class TeamRole {
    OWNER, ADMIN, EDITOR, VIEWER;

    /** Управление составом: приглашать, удалять, менять роли. */
    val canManageMembers: Boolean get() = this == OWNER || this == ADMIN

    /** Запись/шеринг общих записей команды. */
    val canWrite: Boolean get() = this == OWNER || this == ADMIN || this == EDITOR

    /** Просмотр аудит-лога команды. */
    val canViewAudit: Boolean get() = this == OWNER || this == ADMIN

    /** Роли, которые эта роль вправе назначать при приглашении/смене (анти-эскалация). */
    fun assignableRoles(): List<TeamRole> = when (this) {
        OWNER -> listOf(ADMIN, EDITOR, VIEWER)
        ADMIN -> listOf(EDITOR, VIEWER)
        else -> emptyList()
    }

    /** Проводное/хранимое представление роли. */
    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): TeamRole = when (value) {
            "owner" -> OWNER
            "admin" -> ADMIN
            "editor", "member" -> EDITOR
            else -> VIEWER
        }
    }
}

/** Строка аудит-лога команды: актор, событие, человекочитаемая сводка (без содержимого записей). */
class TeamActivityEntry(
    val actorAccountId: String,
    val event: String,
    val detail: String,
    val createdAt: Long,
)

/** Статус членства. Незнакомая строка деградирует в [INVITED] (доступа к записям не даёт). */
enum class TeamMemberStatus { INVITED, ACTIVE;
    companion object {
        fun fromWire(value: String): TeamMemberStatus = if (value == "active") ACTIVE else INVITED
    }
}

/** Команда глазами текущего аккаунта: метаданные + membership + конверт приглашения (пока invited). */
class TeamSummary(
    val id: String,
    val ownerAccountId: String,
    val role: TeamRole,
    val status: TeamMemberStatus,
    val createdAt: Long,
    val memberCount: Int,
    val envelope: ByteArray?,
)

class TeamMember(
    val accountId: String,
    val role: TeamRole,
    val status: TeamMemberStatus,
    val createdAt: Long,
)

/**
 * Сетевой контракт Teams (`/account/key*`, `/teams*`) — стейтлесс, все методы принимают
 * [SyncSession]. Ошибки — [app.skerry.shared.sync.SyncException] с теми же Kind, что у SyncClient.
 * Реализуется тем же [app.skerry.shared.sync.SyncClient]-транспортом (KtorSyncClient).
 */
interface TeamClient {
    /** Публикует публичную X25519-половину identity-пары аккаунта. */
    suspend fun publishKey(session: SyncSession, publicKey: ByteArray)

    /** Публичный ключ другого аккаунта; null — аккаунт ещё не включал Teams (ключ не опубликован). */
    suspend fun fetchPublicKey(session: SyncSession, accountId: String): ByteArray?

    suspend fun createTeam(session: SyncSession, teamId: String)

    suspend fun listTeams(session: SyncSession): List<TeamSummary>

    suspend fun members(session: SyncSession, teamId: String): List<TeamMember>

    /** Приглашает [accountId] с ролью [role] (сервер отвергает эскалацию выше прав приглашающего). */
    suspend fun invite(session: SyncSession, teamId: String, accountId: String, role: TeamRole, envelope: ByteArray)

    suspend fun accept(session: SyncSession, teamId: String)

    /** Меняет роль участника (owner/admin; сервер применяет анти-эскалацию, owner неизменяем). */
    suspend fun changeRole(session: SyncSession, teamId: String, accountId: String, role: TeamRole)

    /** Аудит-лог команды (owner/admin); свежие события первыми. */
    suspend fun teamActivity(session: SyncSession, teamId: String): List<TeamActivityEntry>

    /** Удаление участника владельцем, выход из команды или отклонение приглашения (target = сам). */
    suspend fun removeMember(session: SyncSession, teamId: String, accountId: String)

    suspend fun deleteTeam(session: SyncSession, teamId: String)

    suspend fun pullTeam(session: SyncSession, teamId: String, since: Long): RecordPage

    suspend fun pushTeam(session: SyncSession, teamId: String, records: List<RemoteRecord>): RecordPage
}
