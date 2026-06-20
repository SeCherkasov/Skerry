package app.skerry.ui.design

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import app.skerry.ui.host.HostManagerController

/**
 * Живые бэкенды, подаваемые в дизайн-слой через CompositionLocal (тем же приёмом, что [LocalFonts]) —
 * чтобы не протаскивать контроллеры параметрами через каждый composable макета. `null` означает
 * мок-путь (офскрин-рендер/превью): composable рисует статичные данные [DesktopMockData].
 *
 * [DesktopDesignApp] поставляет их за гейтом vault; по мере разводки бэкендов сюда добавятся
 * соединение/SFTP/форвардинг следующими слайсами.
 */
val LocalHosts: ProvidableCompositionLocal<HostManagerController?> = staticCompositionLocalOf { null }
