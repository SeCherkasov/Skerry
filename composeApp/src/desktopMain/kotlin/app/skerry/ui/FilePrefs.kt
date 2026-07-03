package app.skerry.ui

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Персист простых UI-префов desktop: каждое значение — маленький файл `name` в каталоге [dir]
 * (`~/.config/skerry`). Один общий шаблон вместо пар readX/writeX: чтение отдаёт дефолт при
 * отсутствии/нечитаемости файла, запись — best-effort (сбой персиста не роняет UI).
 */
internal class FilePrefs(private val dir: Path) {

    /**
     * `0`/`1`-флаг. Семантика прежних read*-функций сохранена: при [default] = true всё, кроме «0»,
     * читается как true (`info_panel` и т.п.); при false — true только явная «1» (`terminal_show_title`).
     */
    fun bool(name: String, default: Boolean): Boolean =
        runCatching { if (default) raw(name) != "0" else raw(name) == "1" }.getOrDefault(default)

    /** Целое значение; отсутствует/нечитаемо → [default]. Диапазон/допустимость проверяет вызывающий. */
    fun int(name: String, default: Int): Int =
        runCatching { raw(name).toInt() }.getOrDefault(default)

    /** Значение по стабильному строковому представлению: [parse] бросает на неизвестном → [default]. */
    fun <T> id(name: String, default: T, parse: (String) -> T): T =
        runCatching { parse(raw(name)) }.getOrDefault(default)

    /** Список строк по одной на строку файла (порядок значим); отсутствует/нечитаем → пусто. */
    fun lines(name: String): List<String> =
        runCatching {
            Files.readAllLines(dir.resolve(name), StandardCharsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

    fun set(name: String, value: Boolean) = set(name, if (value) "1" else "0")

    fun set(name: String, value: Int) = set(name, value.toString())

    /**
     * Запись списка построчно. Значения с переносами строк не хранимы построчно — исключаем их, чтобы
     * файл не «расщепился» при чтении (readAllLines рвёт строки и по \n, и по \r, и по \r\n).
     */
    fun setLines(name: String, values: List<String>) =
        set(name, values.filterNot { it.contains('\n') || it.contains('\r') }.joinToString("\n"))

    /** Сырая запись значения (id/число-строкой); сбой персиста проглатывается — UI важнее файла. */
    fun set(name: String, value: String) {
        runCatching {
            Files.createDirectories(dir)
            Files.writeString(dir.resolve(name), value)
        }
    }

    private fun raw(name: String): String = Files.readString(dir.resolve(name)).trim()
}
