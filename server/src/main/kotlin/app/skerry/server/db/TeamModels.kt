package app.skerry.server.db

/** Команда в том виде, как её видит сервер: только метаданные, имени и ключа у сервера нет. */
data class TeamRow(
    val id: String,
    val ownerAccountId: String,
    val teamSeq: Long,
    val createdAt: Long,
)

/**
 * Участник команды. [envelope] — sealed-конверт с teamKey для приглашённого (см. [TeamMembers]);
 * очищается при принятии приглашения: дальше teamKey живёт в собственном vault участника.
 */
data class TeamMemberRow(
    val teamId: String,
    val accountId: String,
    val role: String,
    val status: String,
    val envelope: ByteArray?,
    val invitedBy: String,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TeamMemberRow) return false
        return teamId == other.teamId && accountId == other.accountId && role == other.role &&
            status == other.status && invitedBy == other.invitedBy && createdAt == other.createdAt &&
            (envelope?.contentEquals(other.envelope ?: return false) ?: (other.envelope == null))
    }

    override fun hashCode(): Int {
        var result = teamId.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (envelope?.contentHashCode() ?: 0)
        result = 31 * result + invitedBy.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

/** Членство текущего аккаунта + метаданные команды — строка ответа `GET /teams`. */
data class TeamMembershipView(
    val team: TeamRow,
    val role: String,
    val status: String,
    val envelope: ByteArray?,
    val memberCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TeamMembershipView) return false
        return team == other.team && role == other.role && status == other.status &&
            memberCount == other.memberCount &&
            (envelope?.contentEquals(other.envelope ?: return false) ?: (other.envelope == null))
    }

    override fun hashCode(): Int {
        var result = team.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (envelope?.contentHashCode() ?: 0)
        result = 31 * result + memberCount
        return result
    }
}

/**
 * Роли и статусы участников — единственные допустимые значения на проводе и в БД, плюс
 * capability-логика ACL (единый источник для маршрутов). Гранулярность гейтит запись/управление,
 * НЕ чтение: у любого активного участника есть teamKey, поэтому «viewer, не видящий секретов»
 * криптографически невозможен без ротации ключа. Иерархия прав: OWNER > ADMIN > EDITOR > VIEWER.
 */
object TeamRoles {
    const val OWNER = "owner"
    const val ADMIN = "admin"
    const val EDITOR = "editor"
    const val VIEWER = "viewer"

    /** Legacy до гранулярных ролей: любой активный участник мог писать записи — эквивалент EDITOR. */
    const val MEMBER = "member"

    /** Роли, которые можно назначить приглашением/сменой (OWNER фиксирован за создателем). */
    val ASSIGNABLE = setOf(ADMIN, EDITOR, VIEWER)

    /** Управление участниками: приглашать, удалять, менять роли. */
    fun canManageMembers(role: String): Boolean = role == OWNER || role == ADMIN

    /** Запись/шеринг команды: push/share/unshare записей. */
    fun canWrite(role: String): Boolean = role == OWNER || role == ADMIN || role == EDITOR || role == MEMBER

    /** Просмотр аудит-лога команды. */
    fun canViewAudit(role: String): Boolean = role == OWNER || role == ADMIN

    /** Вправе ли [actorRole] назначить/приглашать роль [targetRole] (анти-эскалация привилегий). */
    fun canAssign(actorRole: String, targetRole: String): Boolean {
        if (targetRole !in ASSIGNABLE) return false
        return when (actorRole) {
            OWNER -> true
            ADMIN -> targetRole == EDITOR || targetRole == VIEWER
            else -> false
        }
    }

    /** Вправе ли [actorRole] удалить/сменить участника с ролью [targetRole] (анти-эскалация). */
    fun canModifyMember(actorRole: String, targetRole: String): Boolean = when (actorRole) {
        OWNER -> targetRole != OWNER
        ADMIN -> targetRole == EDITOR || targetRole == VIEWER || targetRole == MEMBER
        else -> false
    }
}

object TeamMemberStatus {
    const val INVITED = "invited"
    const val ACTIVE = "active"
}
