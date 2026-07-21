package app.skerry.shared.agent

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The `SSH_AUTH_SOCK` socket, driven by a client that speaks the real agent protocol — the role
 * `ssh` plays when it asks the agent for keys.
 */
class UnixSocketSshAgentTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: Path
    private var agent: UnixSocketSshAgent? = null

    @AfterTest
    fun tearDown() {
        agent?.stop()
        scope.cancel()
        if (::dir.isInitialized) Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
    }

    private fun start(identities: List<SshAgentIdentity>): Path {
        dir = Files.createTempDirectory("skerry-agent-test")
        val keys = object : SshAgentKeys {
            override suspend fun identities() = identities
            override suspend fun sign(keyBlob: ByteArray, data: ByteArray, flags: Int): SshAgentSignature? = null
        }
        return UnixSocketSshAgent(dir.resolve("run"), SshAgentService(keys), scope)
            .also { agent = it }
            .start()
    }

    /** One agent round trip over the socket, framing included. */
    private fun request(path: Path, message: ByteArray): ByteArray =
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(path))
            val output = Channels.newOutputStream(channel)
            output.write(SshAgentCodec.frame(message))
            output.flush()
            val input = Channels.newInputStream(channel)
            val header = ByteArray(4).also { input.readNBytes(it, 0, 4) }
            val length = (header[0].toInt() and 0xFF shl 24) or (header[1].toInt() and 0xFF shl 16) or
                (header[2].toInt() and 0xFF shl 8) or (header[3].toInt() and 0xFF)
            input.readNBytes(length)
        }

    @Test
    fun `serves the identity list over the socket`() = runTest {
        val identities = listOf(SshAgentIdentity(byteArrayOf(1, 2, 3), "work key"))
        val path = start(identities)
        val response = withTimeout(SOCKET_TIMEOUT_MILLIS) { request(path, byteArrayOf(11)) }
        assertContentEquals(SshAgentCodec.identitiesAnswer(identities), response)
    }

    @Test
    fun `serves several clients in a row`() = runTest {
        // `ssh` opens a new connection per invocation; one client hanging up must not end the agent.
        val path = start(emptyList())
        repeat(3) {
            val response = withTimeout(SOCKET_TIMEOUT_MILLIS) { request(path, byteArrayOf(11)) }
            assertContentEquals(SshAgentCodec.identitiesAnswer(emptyList()), response)
        }
    }

    @Test
    fun `the socket is reachable only by its owner`() {
        // The socket has no other access control: anything that can open it can make the agent sign.
        val path = start(emptyList())
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(path),
        )
        assertEquals(
            PosixFilePermission.entries.filter { it.name.startsWith("OWNER") }.toSet(),
            Files.getPosixFilePermissions(path.parent),
        )
    }

    @Test
    fun `stop removes the socket`() {
        val path = start(emptyList())
        assertTrue(Files.exists(path))
        agent?.stop()
        assertFalse(Files.exists(path), "socket file outlived the agent")
    }

    @Test
    fun `start replaces a socket left behind by a crash`() {
        val path = start(emptyList())
        agent?.stop()
        Files.createFile(path)
        assertEquals(path, agent?.start())
    }

    private companion object {
        const val SOCKET_TIMEOUT_MILLIS = 10_000L
    }
}
