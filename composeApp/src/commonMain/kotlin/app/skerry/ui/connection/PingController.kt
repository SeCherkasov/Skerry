package app.skerry.ui.connection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Периодически замеряет RTT до сервера через [measure] (один round-trip на цикл) и публикует его в
 * [rttMs] (мс) для статус-бара. Гоняется на [scope] сессии (как
 * [app.skerry.ui.metrics.HostMetricsController]): переживает переключение вкладок и останавливается
 * вместе с сессией ([stop] из [ConnectionController.disconnect]).
 *
 * Неудачный замер (обрыв, таймаут → [measure] вернул `null` или бросил) НЕ сбрасывает индикатор:
 * [rttMs] держит последнее удачное значение до следующего успешного цикла. Первый замер — сразу.
 */
@Stable
class PingController(
    private val measure: suspend () -> Long?,
    private val scope: CoroutineScope,
    private val pollIntervalMillis: Long = 5000,
) {
    var rttMs: Long? by mutableStateOf(null)
        private set

    private var job: Job? = null

    /** Запустить периодический замер (идемпотентно: повторный вызов не плодит второй цикл). */
    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                runCatching { measure() }
                    .onFailure { if (it is CancellationException) throw it } // отмену не глотаем
                    .getOrNull()
                    ?.let { rttMs = it }
                delay(pollIntervalMillis)
            }
        }
    }

    /** Остановить замер. */
    fun stop() {
        job?.cancel()
        job = null
    }
}
