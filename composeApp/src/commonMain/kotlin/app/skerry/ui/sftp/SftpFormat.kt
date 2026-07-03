package app.skerry.ui.sftp

import kotlin.math.roundToLong

/** Двоичные единицы размера (1 KB = 1024 B), как привычно для файловых менеджеров. */
private val SIZE_UNITS = listOf("KB", "MB", "GB", "TB", "PB")

/**
 * Человекочитаемый размер: ниже 1 КиБ — сырые байты («96 B»), выше — одна десятичная и двоичная
 * единица («1.5 KB», «418.0 MB»). Без `String.format` (нет в commonMain) — десятая часть руками.
 */
fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < SIZE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    var tenths = (value * 10).roundToLong()
    // Округление может «дотянуть» до 1024.0 текущей единицы (напр. 1048575 B → 1024.0 KB); тогда
    // переносим в следующую единицу, чтобы показать «1.0 MB», а не «1024.0 KB».
    if (tenths >= 10_240 && unit < SIZE_UNITS.lastIndex) {
        unit++
        tenths = 10
    }
    return "${tenths / 10}.${tenths % 10} ${SIZE_UNITS[unit]}"
}
