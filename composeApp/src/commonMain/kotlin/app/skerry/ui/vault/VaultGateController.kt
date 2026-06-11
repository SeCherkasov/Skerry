package app.skerry.ui.vault

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skerry.shared.vault.UnlockResult
import app.skerry.shared.vault.Vault

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
    private val minPasswordLength: Int = MIN_MASTER_PASSWORD_LENGTH,
) {
    var state: VaultGateState by mutableStateOf(
        if (vault.exists()) VaultGateState.NeedsUnlock else VaultGateState.NeedsCreate,
    )
        private set

    var error: VaultGateError? by mutableStateOf(null)
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
}
