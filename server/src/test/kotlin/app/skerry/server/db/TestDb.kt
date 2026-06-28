package app.skerry.server.db

import org.jetbrains.exposed.sql.Database
import java.nio.file.Files

/**
 * Изолированная SQLite-БД на временном файле для тестов репозиториев. Файл, а не in-memory:
 * Exposed открывает на транзакцию свой коннект, а `:memory:` у каждого коннекта свой.
 */
fun withTestDb(block: (Database) -> Unit) {
    val file = Files.createTempFile("skerry-test-", ".db")
    try {
        val db = Database.connect("jdbc:sqlite:${file.toAbsolutePath()}", driver = "org.sqlite.JDBC")
        Db.createSchema(db)
        block(db)
    } finally {
        Files.deleteIfExists(file)
    }
}

fun seedAccount(db: Database, accountId: String = "alice@example.com") {
    AccountRepository(db).create(
        accountId = accountId,
        srpSalt = "00",
        srpVerifier = "ab",
        wrappedDataKey = byteArrayOf(1, 2, 3),
    )
}
