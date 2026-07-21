package app.skerry.shared.agent

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Wire-level tests for the agent protocol codec (draft-miller-ssh-agent-04 subset). */
class SshAgentCodecTest {

    private fun message(type: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(byteArrayOf(type.toByte())) { acc, field -> acc + field }
        return body
    }

    private fun string(bytes: ByteArray): ByteArray =
        byteArrayOf(
            (bytes.size ushr 24).toByte(),
            (bytes.size ushr 16).toByte(),
            (bytes.size ushr 8).toByte(),
            bytes.size.toByte(),
        ) + bytes

    private fun uint32(value: Int): ByteArray =
        byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    @Test
    fun `parses a request for identities`() {
        assertIs<SshAgentRequest.ListIdentities>(SshAgentCodec.parseRequest(byteArrayOf(11)))
    }

    @Test
    fun `parses a sign request with key, data and flags`() {
        val request = SshAgentCodec.parseRequest(
            message(13, string(byteArrayOf(1, 2, 3)), string(byteArrayOf(9, 9)), uint32(4)),
        )
        val sign = assertIs<SshAgentRequest.Sign>(request)
        assertContentEquals(byteArrayOf(1, 2, 3), sign.keyBlob)
        assertContentEquals(byteArrayOf(9, 9), sign.data)
        assertEquals(SshAgentCodec.FLAG_RSA_SHA2_512, sign.flags)
    }

    @Test
    fun `a sign request may omit the flags field`() {
        // OpenSSH always sends flags, but the field is the last one: a client that stops short must
        // be read as "no flags" rather than dropped, otherwise SHA-1 RSA clients break.
        val request = SshAgentCodec.parseRequest(message(13, string(byteArrayOf(1)), string(byteArrayOf(2))))
        assertEquals(0, assertIs<SshAgentRequest.Sign>(request).flags)
    }

    @Test
    fun `unknown message types are reported as unsupported, not rejected`() {
        // Add/remove/lock (17..22) and extensions (27) are answered with FAILURE by the service; the
        // codec must classify them instead of throwing, so the audit can tell them from garbage.
        assertEquals(SshAgentRequest.Unsupported(17), SshAgentCodec.parseRequest(byteArrayOf(17)))
    }

    @Test
    fun `an empty message is malformed`() {
        assertFailsWith<SshAgentProtocolException> { SshAgentCodec.parseRequest(ByteArray(0)) }
    }

    @Test
    fun `a truncated sign request is malformed`() {
        assertFailsWith<SshAgentProtocolException> {
            SshAgentCodec.parseRequest(message(13, string(byteArrayOf(1, 2, 3)), byteArrayOf(0, 0, 0)))
        }
    }

    @Test
    fun `a field longer than the message is malformed`() {
        // Length prefix from an untrusted peer must be bounded by what actually arrived, or the
        // reader would try to allocate/copy 4 GiB.
        assertFailsWith<SshAgentProtocolException> {
            SshAgentCodec.parseRequest(message(13, uint32(Int.MAX_VALUE) + byteArrayOf(1)))
        }
    }

    @Test
    fun `encodes the identities answer`() {
        val encoded = SshAgentCodec.identitiesAnswer(
            listOf(
                SshAgentIdentity(byteArrayOf(7, 7), "work key"),
                SshAgentIdentity(byteArrayOf(8), "home key"),
            ),
        )
        val expected = byteArrayOf(12) + uint32(2) +
            string(byteArrayOf(7, 7)) + string("work key".encodeToByteArray()) +
            string(byteArrayOf(8)) + string("home key".encodeToByteArray())
        assertContentEquals(expected, encoded)
    }

    @Test
    fun `encodes an empty identities answer`() {
        assertContentEquals(byteArrayOf(12) + uint32(0), SshAgentCodec.identitiesAnswer(emptyList()))
    }

    @Test
    fun `encodes a signature response`() {
        assertContentEquals(
            byteArrayOf(14) + string(byteArrayOf(5, 6)),
            SshAgentCodec.signResponse(byteArrayOf(5, 6)),
        )
    }

    @Test
    fun `failure is a single byte`() {
        assertContentEquals(byteArrayOf(5), SshAgentCodec.failure())
    }

    @Test
    fun `frames a message with its length`() {
        assertContentEquals(uint32(3) + byteArrayOf(1, 2, 3), SshAgentCodec.frame(byteArrayOf(1, 2, 3)))
    }
}
