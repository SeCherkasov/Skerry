package app.skerry.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/** Агрегаты для админ-консоли. Только счётчики — никакого доступа к содержимому. */
class StatsRepository(private val db: Database) {
    data class Counts(val accounts: Long, val devices: Long, val records: Long, val pairingSessions: Long)

    fun counts(): Counts = transaction(db) {
        Counts(
            accounts = Accounts.selectAll().count(),
            devices = Devices.selectAll().count(),
            records = Records.selectAll().count(),
            pairingSessions = Pairing.selectAll().count(),
        )
    }
}
