package app.skerry.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.skerry.shared.vault.AndroidBiometricKeyStore
import app.skerry.shared.vault.FileBioArtifactStore
import app.skerry.shared.vault.FileVault
import app.skerry.shared.vault.IdentityStore
import app.skerry.shared.vault.IonspinVaultCrypto
import app.skerry.shared.vault.VaultBiometrics
import app.skerry.shared.vault.initializeVaultCrypto
import app.skerry.ui.App
import app.skerry.ui.AppDependencies
import app.skerry.ui.identity.IdentityManagerController
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Точка входа Android. [FragmentActivity] (а не `ComponentActivity`) обязателен для
 * `androidx.biometric.BiometricPrompt`. Граф зависимостей строится здесь по образцу desktop
 * `main.kt`: локальный зашифрованный vault в приватном `filesDir`, та же кросс-платформенная
 * крипта (ionspin) и okio-стор. SSH-транспорт на Android пока не подключён (паритет в работе),
 * поэтому за гейтом — настройки vault (биометрия + lock) до прихода полноценного мобильного UI.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // libsodium (ionspin) требует асинхронной инициализации до первого вызова VaultCrypto;
        // на старте делаем это блокирующе, как desktop, чтобы граф строился уже готовым.
        runBlocking { initializeVaultCrypto() }

        val deps = buildDependencies()
        setContent { App(deps) }
    }

    private fun buildDependencies(): AppDependencies {
        val dir = filesDir // приватный каталог приложения
        val crypto = IonspinVaultCrypto()
        val vault = FileVault(
            dir.resolve("vault.json").absolutePath.toPath(),
            crypto,
            deviceId(dir),
            FileSystem.SYSTEM,
        ) { System.currentTimeMillis().toString() }
        val identities = IdentityManagerController(IdentityStore(vault)) { UUID.randomUUID().toString() }
        // Биометрия: ключ в AndroidKeyStore, промпт хостит эта Activity. Слабая ссылка — стор не
        // удерживает Activity и при пересоздании отдаёт null, а не уничтоженную (промпт тогда NoActivity).
        val activityRef = WeakReference(this)
        val biometrics = VaultBiometrics(
            vault = vault,
            keyStore = AndroidBiometricKeyStore(applicationContext) { activityRef.get() },
            artifacts = FileBioArtifactStore(dir.resolve("vault.bio").absolutePath.toPath(), FileSystem.SYSTEM),
            deviceId = deviceId(dir),
        )
        return AppDependencies(vault = vault, identities = identities, biometrics = biometrics)
    }

    /** Стабильный идентификатор устройства для записей vault (provenance + LWW будущего sync). */
    private fun deviceId(dir: File): String {
        val file = File(dir, "device_id")
        if (file.exists()) return file.readText().trim()
        val id = UUID.randomUUID().toString()
        file.writeText(id)
        return id
    }
}
