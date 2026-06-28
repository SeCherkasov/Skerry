package app.skerry.server.auth

import com.nimbusds.srp6.SRP6CryptoParams
import com.nimbusds.srp6.SRP6Exception
import com.nimbusds.srp6.SRP6ServerSession
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Серверная сторона SRP-6a (`docs/skerry-sync-design.md` §1, §3). Сервер хранит только
 * соль `s` и верификатор `v` (см. [app.skerry.server.db.Accounts]); пароль/authKey клиента
 * никогда не передаётся. Вход — двухшаговый: challenge выдаёт эфемерный `B`, verify проверяет
 * доказательство `M1` клиента и возвращает встречное `M2`.
 *
 * Между двумя HTTP-запросами серверная сессия Nimbus (с приватным `b`) держится в памяти под
 * одноразовым [challengeId] с TTL — модель одиночного self-hosted инстанса.
 */
class SrpService(
    private val clock: () -> Long = System::currentTimeMillis,
    private val challengeTtlMillis: Long = 120_000,
    /** Жёсткий предел незавершённых challenge — страховка от OOM при флуде /auth/srp/challenge. */
    private val maxPending: Int = 10_000,
    private val randomId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    /** Стандартные параметры: 2048-битная группа RFC 5054, хеш SHA-256. */
    val params: SRP6CryptoParams = SRP6CryptoParams.getInstance(2048, "SHA-256")

    private data class Pending(val session: SRP6ServerSession, val accountId: String, val createdAt: Long)

    private val pending = ConcurrentHashMap<String, Pending>()

    data class Challenge(val challengeId: String, val salt: String, val b: String)

    /** Шаг 1: по соли/верификатору аккаунта порождает эфемерный `B` и регистрирует challenge. */
    fun startChallenge(accountId: String, salt: String, verifier: String): Challenge {
        evictExpired()
        // Под предел: если флуд не даёт TTL-эвикции справиться, сбрасываем самые старые challenge.
        if (pending.size >= maxPending) {
            pending.entries.sortedBy { it.value.createdAt }
                .take(pending.size - maxPending + 1)
                .forEach { pending.remove(it.key) }
        }
        val session = SRP6ServerSession(params)
        val b = session.step1(accountId, BigInteger(salt, 16), BigInteger(verifier, 16))
        val challengeId = randomId()
        pending[challengeId] = Pending(session, accountId, clock())
        return Challenge(challengeId, salt, b.toString(16))
    }

    /**
     * Шаг 2: проверяет доказательство клиента `M1` и возвращает встречное `M2` (hex) с
     * accountId, либо `null` при неверном пароле/просроченном или неизвестном challenge.
     * Challenge одноразовый — снимается при любом исходе.
     */
    fun verify(challengeId: String, a: String, m1: String): Verified? {
        evictExpired()
        val p = pending.remove(challengeId) ?: return null
        if (clock() - p.createdAt > challengeTtlMillis) return null
        return try {
            val m2 = p.session.step2(BigInteger(a, 16), BigInteger(m1, 16))
            Verified(p.accountId, m2.toString(16))
        } catch (_: SRP6Exception) {
            null
        }
    }

    data class Verified(val accountId: String, val m2: String)

    private fun evictExpired() {
        val now = clock()
        pending.entries.removeIf { now - it.value.createdAt > challengeTtlMillis }
    }
}
