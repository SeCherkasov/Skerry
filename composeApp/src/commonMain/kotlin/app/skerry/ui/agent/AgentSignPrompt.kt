package app.skerry.ui.agent

import androidx.compose.runtime.Composable
import app.skerry.shared.agent.SshAgentOrigin
import app.skerry.ui.design.ConfirmActionDialog
import app.skerry.ui.design.D
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.agent_origin_session
import app.skerry.ui.generated.resources.agent_origin_socket
import app.skerry.ui.generated.resources.agent_prompt_allow
import app.skerry.ui.generated.resources.agent_prompt_body
import app.skerry.ui.generated.resources.agent_prompt_deny
import app.skerry.ui.generated.resources.agent_prompt_hint
import app.skerry.ui.generated.resources.agent_prompt_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Allow this signature?" — the prompt behind Settings → SSH agent → confirm every signature.
 *
 * Hosted at the root of both shells, above every session, because the request comes from the
 * forwarding coroutine rather than from anything the user is looking at. Dismissing (Esc, Refuse)
 * is a real answer: the request is refused, and so is a prompt nobody answers in time
 * ([SshAgentController.PROMPT_TIMEOUT_MS]).
 */
@Composable
fun AgentSignPromptDialog(controller: SshAgentController?) {
    val prompt = controller?.pendingSignature ?: return
    val origin = when (val where = prompt.origin) {
        is SshAgentOrigin.Session -> stringResource(Res.string.agent_origin_session, where.address)
        SshAgentOrigin.LocalSocket -> stringResource(Res.string.agent_origin_socket)
    }
    ConfirmActionDialog(
        title = stringResource(Res.string.agent_prompt_title),
        message = stringResource(Res.string.agent_prompt_body, origin, prompt.keyComment) +
            "\n" + stringResource(Res.string.agent_prompt_hint),
        confirmLabel = stringResource(Res.string.agent_prompt_allow),
        onConfirm = { controller.answerSignature(allow = true) },
        onDismiss = { controller.answerSignature(allow = false) },
        confirmColor = D.cyan,
        dismissLabel = stringResource(Res.string.agent_prompt_deny),
    )
}
