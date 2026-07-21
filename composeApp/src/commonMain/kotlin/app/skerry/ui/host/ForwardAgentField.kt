package app.skerry.ui.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.app.LocalSshAgent
import app.skerry.ui.design.D
import app.skerry.ui.design.Toggle
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.conn_field_forward_agent
import app.skerry.ui.generated.resources.conn_forward_agent_desc
import app.skerry.ui.generated.resources.conn_forward_agent_off
import org.jetbrains.compose.resources.stringResource

/**
 * "Agent forwarding" switch in the host form, shared by the desktop modal and the Android sheet
 * (only the type sizes differ, like the other paired form controls).
 *
 * When the agent itself is off the row still works — the profile setting is remembered — but the
 * description says so, otherwise the switch would silently do nothing at connect time.
 */
@Composable
internal fun ForwardAgentRow(form: NewConnectionFormState) = ForwardAgentToggle(form, titleSize = 12.5.sp, descSize = 11.sp)

/** Android variant of [ForwardAgentRow] (larger type, matching the sheet's other rows). */
@Composable
internal fun MobileForwardAgentRow(form: NewConnectionFormState) = ForwardAgentToggle(form, titleSize = 14.sp, descSize = 11.5.sp)

@Composable
private fun ForwardAgentToggle(
    form: NewConnectionFormState,
    titleSize: androidx.compose.ui.unit.TextUnit,
    descSize: androidx.compose.ui.unit.TextUnit,
) {
    val agentOn = LocalSshAgent.current?.enabled == true
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(stringResource(Res.string.conn_field_forward_agent), color = D.text, size = titleSize)
            Txt(
                if (agentOn) stringResource(Res.string.conn_forward_agent_desc)
                else stringResource(Res.string.conn_forward_agent_off),
                color = if (agentOn) D.dim else D.amber,
                size = descSize,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Toggle(form.forwardAgent, { form.forwardAgent = !form.forwardAgent })
    }
}
