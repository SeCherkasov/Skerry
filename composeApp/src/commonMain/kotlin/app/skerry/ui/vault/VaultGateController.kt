package app.skerry.ui.vault

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skerry.shared.vault.BiometricAvailability
import app.skerry.shared.vault.BiometricEnableResult
import app.skerry.shared.vault.BiometricPrompt
import app.skerry.shared.vault.BiometricUnlockResult
import app.skerry.shared.vault.UnlockResult
import app.skerry.shared.vault.Vault
import app.skerry.shared.vault.VaultBiometrics

/**
 * Минимальная длина мастер-пароля. Выше типичного «8» (NIST для серверных паролей со счётчиком
 * попыток): vault-файл атакуют offline без ограничения попыток, единственный барьер — Argon2id.
 * Единственный источник правды и для валидации, и для текста ошибки в UI.
 */
const val MIN_MASTER_PASSWORD_LENGTH: Int = 12

/** Экран гейта мастер-пароля поверх [Vault]. */
enum class VaultGateState {
    /** Файла vault ещё нет — показываем форму создания мастер-пароля. */
    NeedsCreate,

    /** Vault существует, но заблокирован — показываем форму разблокировки. */
    NeedsUnlock,

    /** Vault разблокирован — пропускаем к остальному UI. */
    Unlocked,
}

/**
 * Причина неуспеха последней попытки. Структурированный тип (не строка), чтобы текст
 * локализовался в UI, а тесты не зависели от формулировок.
 */
enum class VaultGateError {
    /** Пароль короче [VaultGateController.minPasswordLength]. */
    PasswordTooShort,

    /** Пароль и подтверждение не совпали. */
    PasswordMismatch,

    /** Неверный мастер-пароль при разблокировке. */
    WrongPassword,

    /** Файл vault не читается/повреждён. */
    Corrupted,

    /** Биометрия сброшена (новый отпечаток/лицо) — она снята, нужен мастер-пароль. */
    BiometricReset,
}

/**
 * Гейт мастер-пароля: блокирует доступ к остальному UI, пока [Vault] не разблокирован.
 * Стартовое состояние выбирается по [Vault.exists] — создать против разблокировать.
 *
 * [Vault] синхронный (Argon2id-деривация идёт в его реализации), поэтому контроллер, как и
 * [app.skerry.ui.host.HostManagerController], не держит корутинной scope. Пароли приходят
 * как [CharArray] и затираются здесь же: [Vault.create]/[Vault.unlock] затирают переданный
 * буфер по контракту, а подтверждение и не дошедшие до vault буферы гасит сам контроллер.
 */
@Stable
class VaultGateController(
    private val vault: Vault,
    private val biometrics: VaultBiometrics? = null,
    private val minPasswordLength: Int = MIN_MASTER_PASSWORD_LENGTH,
) {
    var state: VaultGateState by mutableStateOf(
        if (vault.exists()) VaultGateState.NeedsUnlock else VaultGateState.NeedsCreate,
    )
        private set

    var error: VaultGateError? by mutableStateOf(null)
        private set

    /** Включена ли биометрия для этого vault (реактивно — тумблер обновляет интерфейс). */
    var biometricEnabled: Boolean by mutableStateOf(biometrics?.isEnabled() == true)
        private set

    /** Счётчик активности пользователя — авто-лок по простою перезапускается при его изменении. */
    var activityTick: Int by mutableStateOf(0)
        private set

    /**
     * Идёт ли сейчас биометрический промпт. Авто-лок при уходе в фон должен его пропускать: системный
     * промпт может слать `ON_STOP`, и блокировка посреди аутентификации привела бы к тому, что
     * пользователь успешно приложил палец, а vault остался заперт (результат уже некому принять).
     */
    var biometricInFlight: Boolean by mutableStateOf(false)
        private set

    /**
     * Создать vault, если пароль проходит валидацию и совпадает с [confirm]. Оба буфера
     * затираются в любом исходе. При ошибке валидации vault не трогается, состояние остаётся
     * [VaultGateState.NeedsCreate].
     */
    fun create(password: CharArray, confirm: CharArray) {
        try {
            error = null
            when {
                password.size < minPasswordLength -> error = VaultGateError.PasswordTooShort
                !password.contentEquals(confirm) -> error = VaultGateError.PasswordMismatch
                else -> {
                    vault.create(password)
                    state = VaultGateState.Unlocked
                }
            }
        } finally {
            password.fill(' ')
            confirm.fill(' ')
        }
    }

    /**
     * Разблокировать существующий vault; на ошибке остаёмся на форме с [error]. Буфер пароля
     * затирается в любом исходе (как в [create]): [Vault.unlock] гасит его по контракту лишь на
     * нормальном возврате, поэтому контроллер страхует и путь с исключением.
     */
    fun unlock(password: CharArray) {
        try {
            error = null
            when (vault.unlock(password)) {
                UnlockResult.Success -> state = VaultGateState.Unlocked
                UnlockResult.WrongPassword -> error = VaultGateError.WrongPassword
                UnlockResult.Corrupted -> error = VaultGateError.Corrupted
            }
        } finally {
            password.fill(' ')
        }
    }

    /** Заблокировать vault и вернуться к форме разблокировки. */
    fun lock() {
        vault.lock()
        error = null
        state = VaultGateState.NeedsUnlock
    }

    /** Зафиксировать активность пользователя — перезапускает таймер авто-лока по простою. */
    fun touch() {
        activityTick++
    }

    /** Можно ли предложить разблокировку биометрией на форме входа (доступна и включена). */
    fun canUnlockWithBiometric(): Boolean =
        biometrics?.let { it.availability() == BiometricAvailability.Available && it.isEnabled() } == true

    /** Можно ли предложить включение биометрии (есть железо и зачислен фактор). */
    fun canEnableBiometric(): Boolean =
        biometrics?.let { it.availability() == BiometricAvailability.Available } == true

    /**
     * Разблокировать биометрией. Успех → [VaultGateState.Unlocked]. Инвалидация ключа снимает
     * биометрию и просит пароль ([VaultGateError.BiometricReset]). Отмена/сбой — тихо остаёмся на
     * форме пароля без ошибки. [prompt] (локализованные строки) приходит из UI.
     */
    suspend fun unlockWithBiometric(prompt: BiometricPrompt) {
        val bio = biometrics ?: return
        error = null
        biometricInFlight = true
        try {
            when (bio.unlock(prompt)) {
                BiometricUnlockResult.Unlocked -> state = VaultGateState.Unlocked
                BiometricUnlockResult.Invalidated -> {
                    biometricEnabled = false
                    error = VaultGateError.BiometricReset
                }
                BiometricUnlockResult.Corrupted -> error = VaultGateError.Corrupted
                // Cancelled / Failed / Unavailable / NotEnabled — остаёмся на форме пароля молча.
                else -> Unit
            }
        } finally {
            biometricInFlight = false
        }
    }

    /** Включить биометрию (vault уже разблокирован). `true`, если включилась. */
    suspend fun enableBiometric(prompt: BiometricPrompt): Boolean {
        val bio = biometrics ?: return false
        biometricInFlight = true
        return try {
            val enabled = bio.enable(prompt) == BiometricEnableResult.Enabled
            biometricEnabled = bio.isEnabled()
            enabled
        } finally {
            biometricInFlight = false
        }
    }

    /** Выключить биометрию (удалить ключ и `vault.bio`). */
    fun disableBiometric() {
        val bio = biometrics ?: return
        bio.disable()
        biometricEnabled = bio.isEnabled()
    }
}
