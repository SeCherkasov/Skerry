package app.skerry.ui.vault

/**
 * Сохранить текстовое содержимое ([content]) в выбранный пользователем файл нативным диалогом
 * «Сохранить как» с предложенным именем [suggestedName]. Используется для экспорта ключа из vault
 * (публичный ключ / приватный PEM). Возвращает `true`, если файл записан, `false` — отмена или
 * платформа без поддержки (мобильный паритет — отдельный шаг).
 */
expect suspend fun exportTextFile(suggestedName: String, content: String): Boolean
