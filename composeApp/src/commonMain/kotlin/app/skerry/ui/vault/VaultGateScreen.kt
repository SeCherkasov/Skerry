package app.skerry.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.skerry.shared.vault.Vault

/**
 * Гейт мастер-пароля: пока [Vault] заблокирован, показывает форму создания или разблокировки;
 * после разблокировки рендерит [content] (остальной UI приложения). Контроллер живёт на время
 * композиции (привязан к идентичности [vault]).
 *
 * Поля ввода Compose оперируют [String], поэтому пароль конвертируется в [CharArray] только на
 * сабмите и сразу затирается контроллером; сам строковый буфер поля неизменяем и живёт до
 * рекомпозиции/очистки сборщиком — известное ограничение текстовых полей, секьюрный ввод
 * (без String в памяти) — отдельный шаг вместе с биометрией.
 */
@Composable
fun VaultGate(
    vault: Vault,
    modifier: Modifier = Modifier,
    content: @Composable (onLock: () -> Unit) -> Unit,
) {
    val controller = remember(vault) { VaultGateController(vault) }

    // key по состоянию: при смене экрана Compose уничтожает и пересоздаёт поддерево формы,
    // чтобы введённый пароль не пережил переход в slot-table (например, после lock()).
    key(controller.state) {
        when (controller.state) {
            VaultGateState.NeedsCreate -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CreateVaultForm(controller.error) { password, confirm -> controller.create(password, confirm) }
            }

            VaultGateState.NeedsUnlock -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                UnlockVaultForm(controller.error) { password -> controller.unlock(password) }
            }

            // lock() переводит гейт в NeedsUnlock; key(state) рушит поддерево content, чей
            // DisposableEffect рвёт живую SSH-сессию — блокировка заодно закрывает сессии.
            VaultGateState.Unlocked -> content { controller.lock() }
        }
    }
}

@Composable
private fun CreateVaultForm(error: VaultGateError?, onCreate: (CharArray, CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val canSubmit = password.isNotEmpty() && confirm.isNotEmpty()

    VaultFormScaffold(
        title = "Создать мастер-пароль",
        subtitle = "Им шифруется локальное хранилище. Пароль не покидает устройство и не " +
            "восстанавливается — забыли его, и данные не расшифровать.",
        error = error,
    ) {
        PasswordField("Мастер-пароль", password, ImeAction.Next) { password = it }
        PasswordField("Повторите пароль", confirm, ImeAction.Done) { confirm = it }
        Button(
            onClick = { if (canSubmit) onCreate(password.toCharArray(), confirm.toCharArray()) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Создать хранилище")
        }
    }
}

@Composable
private fun UnlockVaultForm(error: VaultGateError?, onUnlock: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    val canSubmit = password.isNotEmpty()

    VaultFormScaffold(
        title = "Разблокировать хранилище",
        subtitle = "Введите мастер-пароль, чтобы открыть хосты, ключи и сессии.",
        error = error,
    ) {
        PasswordField("Мастер-пароль", password, ImeAction.Done) { password = it }
        Button(
            onClick = { if (canSubmit) onUnlock(password.toCharArray()) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Разблокировать")
        }
    }
}

@Composable
private fun VaultFormScaffold(
    title: String,
    subtitle: String,
    error: VaultGateError?,
    fields: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        fields()
        if (error != null) {
            Text(error.message(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, imeAction: ImeAction, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun VaultGateError.message(): String = when (this) {
    VaultGateError.PasswordTooShort -> "Пароль слишком короткий — минимум $MIN_MASTER_PASSWORD_LENGTH символов."
    VaultGateError.PasswordMismatch -> "Пароли не совпадают."
    VaultGateError.WrongPassword -> "Неверный мастер-пароль."
    VaultGateError.Corrupted -> "Файл хранилища повреждён или не читается."
}
