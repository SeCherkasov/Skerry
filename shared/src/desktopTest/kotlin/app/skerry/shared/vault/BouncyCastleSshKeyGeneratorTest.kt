package app.skerry.shared.vault

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Генератор проверяется честной интеграцией с парсером sshj (тем же, что грузит ключ при коннекте):
 * сгенерированный PEM должен разбираться `SSHClient.loadKeys`, а независимо посчитанный из него
 * OpenSSH-отпечаток — совпадать с тем, что вернул генератор. Это доказывает, что PEM настоящий и
 * пригоден для аутентификации, без живого сервера. RSA-4096 генерируется один раз (дорого, но это
 * то, что отгружаем). Тесты в desktopTest, т.к. реализация живёт в jvmSharedMain (BouncyCastle/sshj).
 */
class BouncyCastleSshKeyGeneratorTest {

    private val gen = BouncyCastleSshKeyGenerator()

    /** Отпечаток OpenSSH из приватного PEM средствами sshj — независимая проверка формата. */
    private fun fingerprintViaSshj(pem: String): String {
        val keys = SSHClient().loadKeys(pem, null, null)
        val encoded = Buffer.PlainBuffer().putPublicKey(keys.public).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    @Test
    fun `generates ed25519 parseable by sshj with matching fingerprint`() {
        val key = gen.generate(SshKeyType.ED25519, comment = "alice@skerry")

        assertTrue(key.privateKeyPem.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
        assertTrue(key.privateKeyPem.trimEnd().endsWith("-----END OPENSSH PRIVATE KEY-----"))
        assertEquals("ED25519", key.info.keyTypeLabel)
        assertTrue(key.info.publicKeyOpenSsh.startsWith("ssh-ed25519 "))
        assertTrue(key.info.publicKeyOpenSsh.endsWith(" alice@skerry"))
        assertTrue(key.info.fingerprintSha256.startsWith("SHA256:"))
        // Отпечаток генератора == отпечаток, посчитанный из PEM сторонним парсером.
        assertEquals(fingerprintViaSshj(key.privateKeyPem), key.info.fingerprintSha256)
    }

    @Test
    fun `generates rsa-4096 parseable by sshj`() {
        val key = gen.generate(SshKeyType.RSA_4096)

        assertEquals("RSA-4096", key.info.keyTypeLabel)
        assertTrue(key.info.publicKeyOpenSsh.startsWith("ssh-rsa "))
        assertEquals(fingerprintViaSshj(key.privateKeyPem), key.info.fingerprintSha256)
    }

    @Test
    fun `empty comment yields public key without trailing space`() {
        val key = gen.generate(SshKeyType.ED25519, comment = "")
        assertEquals(key.info.publicKeyOpenSsh, key.info.publicKeyOpenSsh.trim())
        assertEquals(2, key.info.publicKeyOpenSsh.split(" ").size)
    }

    @Test
    fun `inspect derives same public metadata from generated private key`() {
        val key = gen.generate(SshKeyType.ED25519, comment = "x@y")
        val info = gen.inspect(key.privateKeyPem)

        assertEquals(key.info.fingerprintSha256, info?.fingerprintSha256)
        assertEquals("ED25519", info?.keyTypeLabel)
        // Публичная часть совпадает по типу и телу (комментарий из PEM не восстанавливается).
        assertTrue(info!!.publicKeyOpenSsh.startsWith("ssh-ed25519 "))
        assertEquals(
            key.info.publicKeyOpenSsh.split(" ")[1],
            info.publicKeyOpenSsh.split(" ")[1],
        )
    }

    @Test
    fun `inspect returns null for garbage`() {
        assertNull(gen.inspect("not a pem at all"))
        assertNull(gen.inspect(""))
    }
}
