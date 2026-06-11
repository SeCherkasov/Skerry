package app.skerry.ui.terminal

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skerry.shared.ssh.PtySize
import app.skerry.shared.terminal.TerminalSession
import app.skerry.shared.terminal.TerminalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Состояние терминального экрана поверх [TerminalSession]. Подписывается на вывод
 * сессии в [scope], накапливает сырые байты и декодирует их в [output] для отрисовки;
 * декодирование идёт по всему буферу, поэтому UTF-8 на границе чанков не бьётся.
 * Ввод и ресайз проксируются в сессию.
 *
 * Это минимальный экран: ANSI/VT-управляющие последовательности пока показываются
 * как есть (полноценный эмулятор — отдельный шаг), а scrollback-буфер не ограничен.
 * Декодирование всего буфера на каждый чанк — O(n) на чанк; для интерактивных сессий
 * приемлемо, а инкрементальный (построчный) декод придёт вместе с VT-эмулятором.
 */
@Stable
class TerminalScreenState(
    private val session: TerminalSession,
    private val scope: CoroutineScope,
) {
    private var raw = ByteArray(0)

    /** Накопленный декодированный вывод PTY; Compose-state — перерисовка на изменении. */
    var output by mutableStateOf("")
        private set

    val state: StateFlow<TerminalState> get() = session.state

    init {
        scope.launch {
            session.output.collect { chunk ->
                raw += chunk
                output = raw.decodeToString()
            }
        }
    }

    /** Отправить введённый текст в PTY (fire-and-forget в [scope]). */
    fun send(text: String) {
        scope.launch { session.send(text.encodeToByteArray()) }
    }

    fun resize(size: PtySize) {
        scope.launch { session.resize(size) }
    }
}
