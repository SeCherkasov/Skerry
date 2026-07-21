package app.skerry.shared.agent

import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.connection.Connection
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import net.schmizz.sshj.connection.channel.forwarded.AbstractForwardedChannel
import net.schmizz.sshj.connection.channel.forwarded.ForwardedChannelOpener

/** Channel type the server opens back to us to reach the agent (OpenSSH's name). */
private const val AGENT_CHANNEL_TYPE = "auth-agent@openssh.com"

/** Session request that asks the server to set `SSH_AUTH_SOCK` for the remote shell. */
private const val AGENT_REQUEST_TYPE = "auth-agent-req@openssh.com"

/**
 * Session channel that can ask for agent forwarding. sshj has no agent support at all (0.40), and
 * `SSHClient.startSession()` hands back a `Session` whose only channel requests are PTY/shell/exec —
 * `sendChannelRequest` is `protected`. Subclassing is the supported way in: this is exactly what
 * `startSession()` builds, plus one extra request.
 *
 * The request must go out BEFORE `shell`/`exec` (sshj marks the channel used up afterwards, and
 * OpenSSH expects the same order).
 */
internal class AgentSessionChannel(conn: Connection, charset: Charset) : SessionChannel(conn, charset) {
    /**
     * Ask the server to forward the agent to this session.
     * @throws ConnectionException the server refused (agent forwarding disabled) or the channel broke
     */
    fun requestAgentForwarding() {
        sendChannelRequest(AGENT_REQUEST_TYPE, true, null)
            .await(conn.timeoutMs.toLong(), TimeUnit.MILLISECONDS)
    }
}

/**
 * Serves agent requests coming back from a server over `auth-agent@openssh.com` channels. Every
 * channel the server opens is answered by the in-process [SshAgentService], so the private key
 * itself never leaves this machine — the remote only ever gets signatures, and only for keys the
 * user put in the agent.
 *
 * Registered on the connection for the whole session (a remote `ssh` may open a channel per
 * connection attempt) and torn down with it via [stop]: sshj does not track third-party openers, so
 * forgetting it is our job, and the scope must die with the session or a wedged channel would
 * outlive the terminal tab.
 */
internal class SshjAgentForwarder(
    private val conn: Connection,
    private val service: SshAgentService,
    private val origin: SshAgentOrigin,
) : ForwardedChannelOpener {

    // Own scope, not a session scope passed in: this object is created inside the transport, below
    // the UI layer, and each channel is a blocking read loop that must be cancellable as a group.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getChannelType(): String = AGENT_CHANNEL_TYPE

    /**
     * Ask the server to forward the agent to [session]. A server with `AllowAgentForwarding no`
     * answers CHANNEL_FAILURE, which must not cost the user their shell: the refusal is recorded
     * for the activity list and the session continues without an agent.
     */
    fun requestOn(session: AgentSessionChannel) {
        try {
            session.requestAgentForwarding()
        } catch (e: IOException) {
            service.note(origin, SshAgentAction.ForwardingDenied)
        }
    }

    override fun handleOpen(buf: SSHPacket) {
        val channel = try {
            AgentChannel(conn, buf.readUInt32AsInt(), buf.readUInt32(), buf.readUInt32())
        } catch (e: Buffer.BufferException) {
            throw ConnectionException(e)
        }
        // confirm() attaches the channel and sends the open confirmation; data can arrive the moment
        // it returns, so the serving coroutine starts right after.
        channel.confirm()
        scope.launch {
            try {
                serveSshAgent(channel.inputStream, channel.outputStream, service, origin)
            } finally {
                runCatching { channel.close() }
            }
        }
    }

    /** De-register from the connection and stop serving. Idempotent. */
    fun stop() {
        conn.forget(this)
        scope.cancel()
    }

    /** An agent channel opened by the server; unlike `x11` it carries no originator address. */
    private class AgentChannel(
        conn: Connection,
        recipient: Int,
        remoteWindowSize: Long,
        remoteMaxPacketSize: Long,
    ) : AbstractForwardedChannel(
        conn,
        AGENT_CHANNEL_TYPE,
        recipient,
        remoteWindowSize,
        remoteMaxPacketSize,
        "",
        0,
    )
}
