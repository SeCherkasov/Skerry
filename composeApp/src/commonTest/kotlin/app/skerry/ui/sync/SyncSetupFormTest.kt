package app.skerry.ui.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Валидация формы настройки sync: http(s)-адрес + accountId + непустой пароль. */
class SyncSetupFormTest {

    @Test
    fun normalizes_trims_fields() {
        val form = SyncSetupForm(serverUrl = "  https://sync.skerry.dev  ", accountId = "  maya  ")
        assertEquals("https://sync.skerry.dev", form.normalizedServerUrl)
        assertEquals("maya", form.normalizedAccountId)
    }

    @Test
    fun complete_form_can_submit() {
        val form = SyncSetupForm(serverUrl = "https://sync.skerry.dev", accountId = "maya")
        assertTrue(form.canSubmit(passwordLength = 8))
    }

    @Test
    fun empty_password_blocks_submit() {
        val form = SyncSetupForm(serverUrl = "https://sync.skerry.dev", accountId = "maya")
        assertFalse(form.canSubmit(passwordLength = 0))
    }

    @Test
    fun blank_account_blocks_submit() {
        val form = SyncSetupForm(serverUrl = "https://sync.skerry.dev", accountId = "   ")
        assertFalse(form.canSubmit(passwordLength = 8))
    }

    @Test
    fun non_http_url_blocks_submit() {
        assertFalse(SyncSetupForm("ssh://host", "maya").canSubmit(8))
        assertFalse(SyncSetupForm("sync.skerry.dev", "maya").canSubmit(8))
        assertFalse(SyncSetupForm("", "maya").canSubmit(8))
        assertFalse(SyncSetupForm("https://", "maya").canSubmit(8)) // схема без хоста
    }

    @Test
    fun http_and_https_both_accepted() {
        assertTrue(SyncSetupForm("http://localhost:8443", "maya").canSubmit(8))
        assertTrue(SyncSetupForm("https://localhost:8443", "maya").canSubmit(8))
    }

    @Test
    fun http_flagged_insecure_but_still_submittable() {
        // http:// разрешён для локального теста (без TLS-прокси), но помечается небезопасным — UI предупреждает.
        val http = SyncSetupForm("http://localhost:8443", "maya")
        assertTrue(http.isInsecureUrl)
        assertTrue(http.canSubmit(8))
    }

    @Test
    fun https_not_flagged_insecure() {
        assertFalse(SyncSetupForm("https://sync.skerry.dev", "maya").isInsecureUrl)
        // Хвостовые пробелы не должны прятать http:// от проверки (нормализуем перед сверкой схемы).
        assertTrue(SyncSetupForm("  http://box.lan  ", "maya").isInsecureUrl)
    }
}
