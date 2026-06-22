package app.skerry.ui.vault

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

/**
 * Desktop-экспорт через нативный AWT [FileDialog] (как [app.skerry.ui.sftp.pickDownloadTarget]).
 * Модальный диалог запускает вложенный цикл событий EDT, поэтому показываем его на [Dispatchers.Swing].
 * Запись делаем на IO-потоке. Отмена (директория/имя == null) → `false`.
 */
actual suspend fun exportTextFile(suggestedName: String, content: String): Boolean {
    val path = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Сохранить как", FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        val dir = dialog.directory ?: return@withContext null
        val name = dialog.file ?: return@withContext null
        File(dir, name).absolutePath
    } ?: return false
    withContext(Dispatchers.IO) { File(path).writeText(content) }
    return true
}
