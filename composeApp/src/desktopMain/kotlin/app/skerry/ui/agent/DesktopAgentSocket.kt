package app.skerry.ui.agent

import app.skerry.shared.agent.SshAgentService
import app.skerry.shared.agent.UnixSocketSshAgent
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope

/**
 * Desktop [SshAgentSocket]: the `SSH_AUTH_SOCK` unix socket other programs connect to. Thin
 * adapter — the listener itself is [UnixSocketSshAgent] in `shared` (so the socket and a forwarded
 * channel serve the identical agent), and this only turns a failed bind into `null` for the UI to
 * report.
 */
internal class DesktopAgentSocket(
    private val directory: Path,
    private val service: SshAgentService,
    private val scope: CoroutineScope,
) : SshAgentSocket {

    private var listener: UnixSocketSshAgent? = null

    override val isSupported: Boolean get() = UnixSocketSshAgent.isSupported

    override fun start(): String? = runCatching {
        val agent = listener ?: UnixSocketSshAgent(directory, service, scope).also { listener = it }
        agent.start().toString()
    }.getOrNull()

    override fun stop() {
        listener?.stop()
    }
}
