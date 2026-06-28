package app.skerry.server.routes

import app.skerry.server.Services
import app.skerry.server.config.ServerConfig
import app.skerry.server.db.Db
import com.nimbusds.srp6.SRP6ClientSession
import com.nimbusds.srp6.SRP6CryptoParams
import com.nimbusds.srp6.SRP6VerifierGenerator
import org.jetbrains.exposed.sql.Database
import java.math.BigInteger
import java.nio.file.Files
import java.security.SecureRandom

val SRP_PARAMS: SRP6CryptoParams = SRP6CryptoParams.getInstance(2048, "SHA-256")

fun testServices(adminToken: String = ""): Services {
    val file = Files.createTempFile("skerry-routes-", ".db")
    file.toFile().deleteOnExit()
    val config = ServerConfig.fromEnv(
        mapOf("SKERRY_DB_URL" to "jdbc:sqlite:${file.toAbsolutePath()}", "SKERRY_ADMIN_TOKEN" to adminToken),
    )
    val database: Database = Db.connect(config)
    return Services(config, database)
}

/** Регистрационный материал SRP, как его посчитал бы клиент перед /auth/register. */
data class SrpRegistration(val salt: String, val verifier: String)

fun srpRegister(accountId: String, password: String): SrpRegistration {
    val salt = BigInteger(256, SecureRandom())
    val verifier = SRP6VerifierGenerator(SRP_PARAMS).generateVerifier(salt, accountId, password)
    return SrpRegistration(salt.toString(16), verifier.toString(16))
}

/** Клиентская SRP-сессия для теста входа: даёт A и M1 по challenge сервера. */
fun srpClient(accountId: String, password: String): SRP6ClientSession =
    SRP6ClientSession().apply { step1(accountId, password) }
