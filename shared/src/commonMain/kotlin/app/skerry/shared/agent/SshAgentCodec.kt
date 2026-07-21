package app.skerry.shared.agent

/**
 * SSH agent protocol (draft-miller-ssh-agent-04) — the read-only subset Skerry serves: list the
 * keys the vault offers and sign a challenge with one of them. Adding, removing, locking and
 * extensions are deliberately NOT implemented: the key set is the vault's, not the client's, so a
 * forwarded remote (or any local process on the socket) must not be able to change it.
 *
 * Pure codec over [ByteArray] — no sockets, no threads, no JVM types — so it lives in `commonMain`
 * and is driven directly from tests (same split as [app.skerry.shared.vnc.RfbCodec] /
 * [app.skerry.shared.telnet.TelnetCodec]). Framing (the uint32 length prefix in front of every
 * message) is [frame]; reading it back belongs to the transport, which alone knows where the bytes
 * come from.
 *
 * Every input here arrives from an untrusted peer: a forwarded agent channel is opened by the
 * remote server, and the local socket is reachable by any process running as the user. Field
 * lengths are therefore bounded by what actually arrived ([SshAgentProtocolException] otherwise)
 * before anything is allocated.
 */
object SshAgentCodec {

    /** Ceiling for one message, as in OpenSSH's agent. Anything larger is refused unread. */
    const val MAX_MESSAGE_BYTES = 256 * 1024

    /** Signature flags: ask an RSA key for a SHA-2 signature instead of the legacy SHA-1 one. */
    const val FLAG_RSA_SHA2_256 = 2
    const val FLAG_RSA_SHA2_512 = 4

    private const val FAILURE = 5
    private const val REQUEST_IDENTITIES = 11
    private const val IDENTITIES_ANSWER = 12
    private const val SIGN_REQUEST = 13
    private const val SIGN_RESPONSE = 14

    /**
     * Parse one agent message (without the length prefix).
     * @throws SshAgentProtocolException the message is empty or a known request is malformed
     */
    fun parseRequest(message: ByteArray): SshAgentRequest {
        val reader = SshWireReader(message)
        return when (val type = reader.readByte()) {
            REQUEST_IDENTITIES -> SshAgentRequest.ListIdentities
            SIGN_REQUEST -> SshAgentRequest.Sign(
                keyBlob = reader.readString(),
                data = reader.readString(),
                // Flags are the last field: a client that omits them means "no flags" (legacy
                // SHA-1 RSA), which is not a protocol error.
                flags = if (reader.remaining >= 4) reader.readUInt32Bits() else 0,
            )
            else -> SshAgentRequest.Unsupported(type)
        }
    }

    /** `SSH_AGENT_IDENTITIES_ANSWER`: the keys we offer, each as (blob, comment). */
    fun identitiesAnswer(identities: List<SshAgentIdentity>): ByteArray {
        val writer = SshWireWriter()
        writer.putByte(IDENTITIES_ANSWER)
        writer.putUInt32(identities.size)
        identities.forEach { identity ->
            writer.putString(identity.keyBlob)
            writer.putString(identity.comment.encodeToByteArray())
        }
        return writer.toByteArray()
    }

    /** `SSH_AGENT_SIGN_RESPONSE`: [signature] is the ssh-wire blob `string alg || string sig`. */
    fun signResponse(signature: ByteArray): ByteArray {
        val writer = SshWireWriter()
        writer.putByte(SIGN_RESPONSE)
        writer.putString(signature)
        return writer.toByteArray()
    }

    /** `SSH_AGENT_FAILURE` — the only refusal the protocol has; it carries no reason. */
    fun failure(): ByteArray = byteArrayOf(FAILURE.toByte())

    /** Prefix [message] with its uint32 length, the framing every agent connection uses. */
    fun frame(message: ByteArray): ByteArray {
        val writer = SshWireWriter()
        writer.putUInt32(message.size)
        writer.putRaw(message)
        return writer.toByteArray()
    }
}

/** One key the agent offers: ssh-wire public key blob plus the comment shown by `ssh-add -l`. */
class SshAgentIdentity(val keyBlob: ByteArray, val comment: String)

/** A parsed client request. Everything outside the served subset lands in [Unsupported]. */
sealed interface SshAgentRequest {
    /** `SSH_AGENTC_REQUEST_IDENTITIES`. */
    data object ListIdentities : SshAgentRequest

    /** `SSH_AGENTC_SIGN_REQUEST`: sign [data] with the key identified by [keyBlob]. */
    class Sign(val keyBlob: ByteArray, val data: ByteArray, val flags: Int) : SshAgentRequest

    /** Any other message type (add/remove/lock/extension) — answered with a failure. */
    data class Unsupported(val type: Int) : SshAgentRequest
}

/** Malformed input from the peer: truncated message or a length prefix past the end of the data. */
class SshAgentProtocolException(message: String) : Exception(message)

/**
 * Minimal ssh-wire reader over an already-received message. Every read is bounded by the message
 * itself, so a hostile length prefix fails instead of allocating.
 */
private class SshWireReader(private val bytes: ByteArray) {
    private var pos = 0

    val remaining: Int get() = bytes.size - pos

    fun readByte(): Int {
        needAtLeast(1)
        return bytes[pos++].toInt() and 0xFF
    }

    /** Read a uint32 as a raw 32-bit mask (agent flags), without range-checking the sign bit. */
    fun readUInt32Bits(): Int {
        needAtLeast(4)
        var value = 0
        repeat(4) { value = (value shl 8) or (bytes[pos++].toInt() and 0xFF) }
        return value
    }

    /** Read a length-prefixed byte string. */
    fun readString(): ByteArray {
        val length = readUInt32Bits()
        // Negative means the peer sent a length above 2^31 — always a lie, the message itself is
        // capped at 256 KiB.
        if (length < 0) throw SshAgentProtocolException("agent field length out of range")
        needAtLeast(length)
        val out = bytes.copyOfRange(pos, pos + length)
        pos += length
        return out
    }

    private fun needAtLeast(count: Int) {
        if (remaining < count) throw SshAgentProtocolException("truncated agent message")
    }
}

/** Minimal ssh-wire writer over a growable byte buffer. */
private class SshWireWriter {
    private var buffer = ByteArray(64)
    private var size = 0

    fun putByte(value: Int) {
        ensureCapacity(1)
        buffer[size++] = value.toByte()
    }

    fun putUInt32(value: Int) {
        ensureCapacity(4)
        buffer[size++] = (value ushr 24).toByte()
        buffer[size++] = (value ushr 16).toByte()
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun putString(bytes: ByteArray) {
        putUInt32(bytes.size)
        putRaw(bytes)
    }

    fun putRaw(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }
}
