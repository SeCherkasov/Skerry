package app.skerry.shared.agent

import app.skerry.shared.ssh.HostKeyVerifier
import app.skerry.shared.ssh.SshAuth
import app.skerry.shared.ssh.SshTarget
import app.skerry.shared.ssh.SshjTransport
import app.skerry.shared.vault.BouncyCastleSshKeyGenerator
import app.skerry.shared.vault.SshKeyType
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.apache.sshd.agent.SshAgent
import org.apache.sshd.agent.local.AgentServerProxy
import org.apache.sshd.agent.local.ProxyAgentFactory
import org.apache.sshd.common.session.ConnectionService
import org.apache.sshd.common.session.Session
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.forward.RejectAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.InteractiveProcessShellFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val USER = "skerry"
private const val PASSWORD = "correct horse battery staple"

/**
 * Agent forwarding against an embedded Apache MINA SSHD server that has agent forwarding enabled:
 * the server opens `auth-agent@openssh.com` channels back to us, exactly as a remote `ssh` would,
 * and gets answers from the in-process agent.
 */
class SshjAgentForwardingTest {

    private lateinit var server: SshServer
    private val generator = BouncyCastleSshKeyGenerator()
    private val challenge = "userauth request".encodeToByteArray()
    private val acceptAll = HostKeyVerifier { _, _, _, _ -> true }

    @BeforeTest
    fun startServer() {
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider()
            setPasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
            shellFactory = InteractiveProcessShellFactory.INSTANCE
            // Both are needed for the server to accept `auth-agent-req@openssh.com`; the factory
            // also gives the test the server side of a forwarded agent — the role a remote `ssh`
            // plays. The filter is left off in the "denied" test below.
            agentFactory = ProxyAgentFactory()
            forwardingFilter = AcceptAllForwardingFilter.INSTANCE
            start()
        }
    }

    @AfterTest
    fun stopServer() {
        server.stop(true)
    }

    private fun target(forwardAgent: Boolean) =
        SshTarget(host = "127.0.0.1", port = server.port, username = USER, forwardAgent = forwardAgent)

    /**
     * The server side of a forwarded agent: opens an `auth-agent@openssh.com` channel back to the
     * client and speaks the agent protocol over it — what `ssh` on the remote host does through
     * `SSH_AUTH_SOCK`. Built straight from the session's connection service (MINA's factory looks
     * its proxies up by an id that only the remote shell's environment knows).
     */
    private fun agentClientFor(session: Session): SshAgent =
        AgentServerProxy(session.getService(ConnectionService::class.java)).createClient()

    private fun agentOf(usages: MutableList<SshAgentUsage>, pem: String) = SshAgentService(
        SshjAgentKeys({ listOf(SshAgentKeyMaterial(id = "c1", comment = "work key", privateKeyPem = pem)) }),
    ) { usages += it }

    @Test
    fun `the server reaches the agent over a forwarded channel`() = runTest {
        val generated = generator.generate(SshKeyType.ED25519, comment = "")
        val usages = mutableListOf<SshAgentUsage>()
        val connection = SshjTransport(acceptAll, agentOf(usages, generated.privateKeyPem))
            .connect(target(forwardAgent = true), SshAuth.Password(PASSWORD))
        try {
            // The shell is what carries the request; without it the server has no reason to forward.
            val shell = connection.openShell()
            val session = server.activeSessions.single()
            agentClientFor(session).use { agent ->
                val identities = agent.identities.toList()
                assertEquals(listOf("work key"), identities.map { it.value })

                val signature = agent.sign(session, identities.single().key, "ssh-ed25519", challenge)
                assertEquals("ssh-ed25519", signature.key)
                val verifier = java.security.Signature.getInstance("Ed25519")
                verifier.initVerify(identities.single().key)
                verifier.update(challenge)
                assertTrue(verifier.verify(signature.value), "forwarded signature does not verify")
            }
            shell.close()
        } finally {
            connection.disconnect()
        }

        assertEquals(listOf(SshAgentAction.Listed, SshAgentAction.Signed), usages.map { it.action })
        // The server asked on behalf of the session we dialed — that is what the activity list shows.
        assertTrue(usages.all { it.origin == SshAgentOrigin.Session("127.0.0.1") }, "unexpected origins: $usages")
    }

    @Test
    fun `a host without the forwarding flag exposes no agent`() = runTest {
        val generated = generator.generate(SshKeyType.ED25519, comment = "")
        val usages = mutableListOf<SshAgentUsage>()
        val connection = SshjTransport(acceptAll, agentOf(usages, generated.privateKeyPem))
            .connect(target(forwardAgent = false), SshAuth.Password(PASSWORD))
        try {
            connection.openShell().close()
            val session = server.activeSessions.single()
            // No opener is registered, so sshj rejects the channel: the server cannot reach the keys
            // even though the very same agent is configured in the app.
            assertFailsWith<IOException> {
                agentClientFor(session).use { it.identities.toList() }
            }
        } finally {
            connection.disconnect()
        }
        assertTrue(usages.isEmpty(), "agent was used without the per-host flag: $usages")
    }

    @Test
    fun `a server that refuses forwarding is recorded and the shell still opens`() = runTest {
        // `AllowAgentForwarding no` is a common server policy; it must cost the user a note in the
        // activity list, not the session they were opening.
        server.forwardingFilter = RejectAllForwardingFilter.INSTANCE
        val generated = generator.generate(SshKeyType.ED25519, comment = "")
        val usages = mutableListOf<SshAgentUsage>()
        val connection = SshjTransport(acceptAll, agentOf(usages, generated.privateKeyPem))
            .connect(target(forwardAgent = true), SshAuth.Password(PASSWORD))
        try {
            assertTrue(connection.openShell().isOpen)
        } finally {
            connection.disconnect()
        }
        assertEquals(listOf(SshAgentAction.ForwardingDenied), usages.map { it.action })
    }

    @Test
    fun `disconnect stops serving the agent`() = runTest {
        val generated = generator.generate(SshKeyType.ED25519, comment = "")
        val usages = mutableListOf<SshAgentUsage>()
        val connection = SshjTransport(acceptAll, agentOf(usages, generated.privateKeyPem))
            .connect(target(forwardAgent = true), SshAuth.Password(PASSWORD))
        val shell = connection.openShell()
        val session = server.activeSessions.single()
        shell.close()
        connection.disconnect()

        // The session is gone, so the agent must be unreachable — a server that kept a handle on the
        // connection must not be able to sign after the user closed the tab.
        assertFailsWith<IOException> {
            agentClientFor(session).use { it.identities.toList() }
        }
    }
}
