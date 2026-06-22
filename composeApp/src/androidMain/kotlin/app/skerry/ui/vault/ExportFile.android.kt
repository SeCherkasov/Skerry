package app.skerry.ui.vault

/**
 * Android-экспорт ключа пока не реализован (мобильный паритет Vault — отдельный шаг, как и остальной
 * мобильный UI раздела). Возвращает `false`, чтобы UI показал кнопку неактивной/без эффекта вместо
 * ложного успеха. Реализация через Storage Access Framework появится вместе с мобильным VaultView.
 */
actual suspend fun exportTextFile(suggestedName: String, content: String): Boolean = false
