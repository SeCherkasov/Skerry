package app.skerry.ui.sync

/** Семантический уровень индикатора синхронизации (UI красит: OK→moss, WARN→amber, ERROR→sunset). */
enum class SyncIndicatorLevel { OK, WARN, ERROR }

/** Что показать в индикаторе sync (status-bar desktop / шапка mobile). null = скрыть. */
data class SyncIndicator(val icon: String, val label: String, val level: SyncIndicatorLevel)

/**
 * Чистая (тестируемая) проекция состояния sync на статус-индикатор. Раньше индикатор показывал ТОЛЬКО
 * доступность сервера по health-пингу ([ServerReachable]) и потому врал «Sync online», когда у ЭТОГО
 * устройства нет рабочей сессии (отозвано/только привязано/после рестарта). Теперь ведущий сигнал —
 * статус сессии ([SyncStatus]); доступность лишь различает online/offline в активном состоянии.
 *
 * - [SyncStatus.Online] + REACHABLE → «Sync online» (OK); + UNREACHABLE → «Sync offline» (ERROR).
 * - [SyncStatus.Busy] → «Syncing…» (WARN).
 * - [SyncStatus.Configured] → «Sync paused» (WARN): привязка есть, но сессии нет (заперто/после
 *   рестарта/отозвано после неудачного refresh) — НЕ «online».
 * - [SyncStatus.Failed] → «Sync error» (ERROR).
 * - Не настроен / ещё не пинговали ([SyncStatus.Disabled] / [ServerReachable.UNKNOWN]) → скрыть.
 */
fun syncIndicator(status: SyncStatus?, reachable: ServerReachable): SyncIndicator? {
    if (status == null || status == SyncStatus.Disabled || reachable == ServerReachable.UNKNOWN) return null
    return when (status) {
        is SyncStatus.Online ->
            if (reachable == ServerReachable.REACHABLE) SyncIndicator("cloud_done", "Sync online", SyncIndicatorLevel.OK)
            else SyncIndicator("cloud_off", "Sync offline", SyncIndicatorLevel.ERROR)
        SyncStatus.Busy -> SyncIndicator("sync", "Syncing…", SyncIndicatorLevel.WARN)
        is SyncStatus.Configured -> SyncIndicator("cloud_off", "Sync paused", SyncIndicatorLevel.WARN)
        is SyncStatus.Failed -> SyncIndicator("cloud_off", "Sync error", SyncIndicatorLevel.ERROR)
        SyncStatus.Disabled -> null // покрыто ранним возвратом; ветка для исчерпывающего when
    }
}
