package app.skerry.ui.app

/**
 * Центральная версия приложения для UI (страница About и т.п.). Пока это значение-константа макета;
 * при настройке релизной сборки его нужно подставлять из Gradle (generated BuildConfig/резюме
 * версии), а не править руками в двух местах.
 */
object AppVersion {
    const val VERSION = "2.4.0"
    const val BUILD = "2026.06.21"

    /** Строка для страницы About: «Version 2.4.0 · build 2026.06.21». */
    const val DISPLAY = "Version $VERSION · build $BUILD"
}
