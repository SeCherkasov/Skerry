package app.skerry.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Одноразовые pairing-сессии (вариант B, design §3). Сервер хранит dataKey зашифрованным
 * одноразовым transferKey и не может его прочитать; сессия живёт до TTL и сгорает при выдаче.
 */
class PairingRepository(private val db: Database) {

    fun create(code: String, accountId: String, encryptedDataKey: ByteArray, expiresAt: Long): Unit = transaction(db) {
        Pairing.insert {
            it[Pairing.code] = code
            it[Pairing.accountId] = accountId
            it[Pairing.encryptedDataKey] = ExposedBlob(encryptedDataKey)
            it[Pairing.expiresAt] = expiresAt
            it[consumed] = false
        }
    }

    /**
     * Атомарно выдаёт и гасит сессию. Возвращает `null`, если кода нет, он уже выдан или истёк
     * (на момент [now]).
     */
    fun consume(code: String, now: Long = System.currentTimeMillis()): PairingRow? = transaction(db) {
        val row = Pairing.selectAll().where { Pairing.code eq code }.singleOrNull()
            ?: return@transaction null
        if (row[Pairing.consumed] || row[Pairing.expiresAt] <= now) return@transaction null
        Pairing.update({ (Pairing.code eq code) and (Pairing.consumed eq false) }) {
            it[consumed] = true
        }
        PairingRow(
            code = row[Pairing.code],
            accountId = row[Pairing.accountId],
            encryptedDataKey = row[Pairing.encryptedDataKey].bytes,
            expiresAt = row[Pairing.expiresAt],
            consumed = true,
        )
    }

    fun cleanupExpired(now: Long = System.currentTimeMillis()): Int = transaction(db) {
        Pairing.deleteWhere { expiresAt lessEq now }
    }
}
