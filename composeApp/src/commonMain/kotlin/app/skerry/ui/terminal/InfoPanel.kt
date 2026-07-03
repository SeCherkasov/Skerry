package app.skerry.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.app.LocalHosts
import app.skerry.ui.app.LocalSessions
import app.skerry.ui.connection.ConnectionUiState
import app.skerry.ui.connection.shortCipher
import app.skerry.ui.design.D
import app.skerry.ui.design.Dot
import app.skerry.ui.design.HLine
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.MeterBar
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.term_auth_identity
import app.skerry.ui.generated.resources.term_auth_password
import app.skerry.ui.generated.resources.term_info_address
import app.skerry.ui.generated.resources.term_info_auth
import app.skerry.ui.generated.resources.term_info_cipher
import app.skerry.ui.generated.resources.term_info_connection
import app.skerry.ui.generated.resources.term_info_host
import app.skerry.ui.generated.resources.term_info_live
import app.skerry.ui.generated.resources.term_info_system
import app.skerry.ui.generated.resources.term_info_uptime
import app.skerry.ui.generated.resources.term_info_user
import app.skerry.ui.generated.resources.term_live_metrics
import app.skerry.ui.generated.resources.term_metric_cpu
import app.skerry.ui.generated.resources.term_metric_disk
import app.skerry.ui.generated.resources.term_metric_memory
import app.skerry.ui.metrics.HostMetrics
import app.skerry.ui.metrics.formatUptime
import app.skerry.ui.session.sessionDotColor
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

// Info-панель терминального view: параметры соединения, live-метрики и SYSTEM-блок активной сессии.

@Composable
internal fun InfoPanel() {
    val mono = LocalFonts.current.mono
    // Живой контекст активной сессии (если есть): профиль хоста из каталога + состояние соединения.
    val sessions = LocalSessions.current
    val hosts = LocalHosts.current
    val active = sessions?.active
    val host = active?.hostId?.let { id -> hosts?.find(id) }
    val live = sessions != null
    val connected = active?.controller?.uiState is ConnectionUiState.Connected
    // Контроллер live-метрик активной сессии (когда подключена). remember безусловный — ключи
    // (id сессии + флаг connected) пересоздают его при смене сессии/подключения, без условного
    // вызова remember. openMetrics идемпотентен (кэш в ConnectionController).
    val liveMetrics = remember(active, connected) {
        if (connected) active.controller.openMetrics() else null
    }?.metrics
    Column(Modifier.width(268.dp).fillMaxHeight().background(D.surface2).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Txt(stringResource(Res.string.term_info_connection), color = D.faint, size = 11.sp, weight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val dot = if (live) sessionDotColor(active?.controller?.uiState) else D.moss
                val liveLabel = stringResource(Res.string.term_info_live)
                val label = if (!live) liveLabel else if (connected) liveLabel else "—"
                Dot(dot)
                Txt(label, color = dot, size = 10.sp)
            }
        }
        HLine()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Host/Address/User — из живого профиля активной сессии; cipher — из транспорта,
            // uptime — из live-метрик (до первого опроса «…»); в мок-режиме — статичные значения.
            val authIdentity = stringResource(Res.string.term_auth_identity)
            val authPassword = stringResource(Res.string.term_auth_password)
            InfoRow(stringResource(Res.string.term_info_host), if (live) (host?.label ?: active?.title ?: "—") else "prod-web-01", mono)
            InfoRow(stringResource(Res.string.term_info_address), if (live) (host?.let { "${it.address}:${it.port}" } ?: "—") else "192.168.1.45:22", mono)
            InfoRow(stringResource(Res.string.term_info_user), if (live) (host?.username ?: "—") else "root", mono)
            InfoRow(stringResource(Res.string.term_info_auth), if (live) (host?.identityId?.let { authIdentity } ?: authPassword) else "id_ed25519", mono)
            InfoRow(stringResource(Res.string.term_info_cipher), if (live) (shortCipher(active?.controller?.cipher) ?: "…") else "aes256-gcm", mono)
            InfoRow(stringResource(Res.string.term_info_uptime), if (live) (liveMetrics?.uptimeSeconds?.let { formatUptime(it) } ?: "…") else "04:12:45", mono)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            Txt(stringResource(Res.string.term_live_metrics), color = D.faint, size = 10.sp, weight = FontWeight.SemiBold, letterSpacing = 0.5.sp, modifier = Modifier.padding(vertical = 8.dp))
            val cpuLabel = stringResource(Res.string.term_metric_cpu)
            val memoryLabel = stringResource(Res.string.term_metric_memory)
            val diskLabel = stringResource(Res.string.term_metric_disk)
            if (!live) {
                // Мок-путь (превью/офскрин): статичные значения.
                Meter(cpuLabel, "34%", 0.34f, D.cyan, D.textBright, mono)
                Meter(memoryLabel, "2.1 / 4 GB", 0.52f, D.moss, D.textBright, mono)
                Meter(diskLabel, "87%", 0.87f, D.sunset, D.sunset, mono)
            } else {
                // Живой опрос ресурсов сессии (контроллер поднят выше). До первого удачного
                // опроса (или на не-Linux хосте) — «…».
                val m = liveMetrics
                val cpu = m?.cpuPercent
                Meter(cpuLabel, cpu?.let { "$it%" } ?: "…", m?.cpuFraction ?: 0f, D.cyan, if ((cpu ?: 0) > 85) D.sunset else D.textBright, mono)
                Meter(memoryLabel, m?.let { "${gb(it.memUsedBytes)} / ${gb(it.memTotalBytes)} GB" } ?: "…", m?.memFraction ?: 0f, D.moss, D.textBright, mono)
                val disk = m?.diskPercent
                Meter(diskLabel, disk?.let { "$it%" } ?: "…", m?.diskFraction ?: 0f, if ((disk ?: 0) > 85) D.sunset else D.cyan, if ((disk ?: 0) > 85) D.sunset else D.textBright, mono)
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Txt(stringResource(Res.string.term_info_system), color = D.faint, size = 10.sp, weight = FontWeight.SemiBold, letterSpacing = 0.5.sp, modifier = Modifier.padding(vertical = 8.dp))
            // Живой блок собирается из фактов хоста (ОС / ядро / CPU+load); до первого опроса — «…».
            // В мок-режиме (превью/офскрин) — статичный текст.
            val systemText = if (live) liveSystemSummary(liveMetrics) else MOCK_SYSTEM
            Txt(systemText, color = D.dim, size = 10.5.sp, font = mono, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: FontFamily) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Txt(label, color = D.faint, size = 11.5.sp)
        Txt(value, color = D.textBright, size = 11.5.sp, font = mono)
    }
}

@Composable
private fun Meter(label: String, value: String, fraction: Float, bar: Color, valueColor: Color, mono: FontFamily) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(label, color = D.dim, size = 11.sp)
            Txt(value, color = valueColor, size = 11.sp, font = mono)
        }
        MeterBar(fraction, bar)
    }
}

/** Байты → строка гигабайт с одним знаком после запятой (десятичные ГБ, как в free -h). */
private fun gb(bytes: Long): String {
    val rounded = (bytes / 1_000_000_000.0 * 10).roundToInt() / 10.0
    return rounded.toString()
}

/** Статичный SYSTEM-блок для мок/офскрин-режима (нет живой сессии). */
private const val MOCK_SYSTEM = "Ubuntu 22.04.4 LTS\nLinux 5.15.0-105 x86_64\n4 vCPU · load 0.42 0.51 0.48"

/**
 * Собирает SYSTEM-блок info-панели из живых фактов хоста: ОС, ядро, строка «N vCPU · load …».
 * Пропускает поля, которых ещё нет (опрос не дошёл / не-Linux). Пусто всё — «…».
 */
private fun liveSystemSummary(m: HostMetrics?): String {
    val cpuLoad = buildString {
        m?.cpuCount?.let { append("$it vCPU") }
        m?.loadAverage?.let {
            if (isNotEmpty()) append(" · ")
            append("load $it")
        }
    }
    val lines = listOfNotNull(
        m?.osName,
        m?.kernel,
        cpuLoad.takeIf { it.isNotEmpty() },
    )
    return if (lines.isEmpty()) "…" else lines.joinToString("\n")
}
