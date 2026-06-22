package app.skerry.ui.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Поллер RTT поверх suspend-замера соединения. Первый замер — сразу при [PingController.start]
 * (как [app.skerry.ui.metrics.HostMetricsController]); сбой/неудача замера держит последнее удачное
 * значение, а не сбрасывает индикатор. Время виртуальное (testScheduler).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PingControllerTest {

    @Test
    fun polls_and_publishes_rtt() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val c = PingController(measure = { 42L }, scope = scope)

        assertNull(c.rttMs)
        c.start()
        assertEquals(42L, c.rttMs)

        c.stop()
        scope.cancel()
    }

    @Test
    fun failed_measure_keeps_last_value() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        val c = PingController(
            measure = { calls++; if (calls == 1) 10L else throw RuntimeException("boom") },
            scope = scope,
            pollIntervalMillis = 5000,
        )

        c.start() // первый замер → 10
        assertEquals(10L, c.rttMs)
        testScheduler.advanceTimeBy(5000); testScheduler.runCurrent() // второй замер бросает
        assertEquals(10L, c.rttMs) // держим последнее удачное

        c.stop()
        scope.cancel()
    }

    @Test
    fun null_measure_keeps_last_value() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        val c = PingController(
            measure = { calls++; if (calls == 1) 7L else null }, // мёртвый round-trip → null
            scope = scope,
            pollIntervalMillis = 5000,
        )

        c.start()
        assertEquals(7L, c.rttMs)
        testScheduler.advanceTimeBy(5000); testScheduler.runCurrent()
        assertEquals(7L, c.rttMs)

        c.stop()
        scope.cancel()
    }

    @Test
    fun start_is_idempotent() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        val c = PingController(measure = { calls++; 1L }, scope = scope)

        c.start()
        val afterFirst = calls
        c.start() // второй цикл не поднимается
        assertEquals(afterFirst, calls)

        c.stop()
        scope.cancel()
    }
}
