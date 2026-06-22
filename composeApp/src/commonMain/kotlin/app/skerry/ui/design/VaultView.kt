package app.skerry.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.shared.host.Host
import app.skerry.shared.vault.Identity
import app.skerry.shared.vault.IdentityAuth
import app.skerry.shared.vault.SshKeyGenerator
import app.skerry.shared.vault.SshKeyType
import app.skerry.shared.vault.SshPublicKeyInfo
import app.skerry.ui.host.HostDraft
import app.skerry.ui.identity.IdentityDraft
import app.skerry.ui.identity.IdentityKind
import app.skerry.ui.identity.IdentityManagerController
import app.skerry.ui.known.shortFingerprint
import app.skerry.ui.vault.VaultCategoryKind
import app.skerry.ui.vault.VaultPresentation
import app.skerry.ui.vault.exportTextFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vault view. С живым [IdentityManagerController] ([LocalIdentities]) рисует реальные секреты из
 * открытого vault: категории с живыми счётчиками, список ключей/паролей, панель деталей (публичный
 * ключ, отпечаток, какие хосты используют), генерацию пары ([LocalSshKeyGenerator]), добавление
 * пароля, копирование/экспорт/удаление (с развязкой хостов [LocalHosts]). Категории без бэкенда
 * (Identities/Certificates) показываются как заглушки 1:1 с макетом. Без контроллера
 * (офскрин-рендер/превью) рисуется статичный макет [MockVaultView].
 */
@Composable
fun VaultView() {
    when (val identities = LocalIdentities.current) {
        null -> MockVaultView()
        else -> LiveVaultView(identities)
    }
}

// ---------------------------------------------------------------------------------------------
// Живой путь: реальные identity vault + генерация/добавление/удаление.
// ---------------------------------------------------------------------------------------------

@Composable
private fun LiveVaultView(identities: IdentityManagerController) {
    val mono = LocalFonts.current.mono
    val hostsController = LocalHosts.current
    val hosts = hostsController?.hosts ?: emptyList()
    val generator = LocalSshKeyGenerator.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val all = identities.identities

    var category by remember { mutableStateOf(VaultCategoryKind.SSH_KEYS) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showGenerate by remember { mutableStateOf(false) }
    var showAddPassword by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Identity?>(null) }

    val items = VaultPresentation.identitiesIn(category, all)
    // Выбор сам падает на первую запись категории, если прежний id исчез (удаление/смена категории).
    val selected = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            VaultSidebar(category, all) { category = it; selectedId = null }
            VLine(D.line)
            Column(Modifier.weight(1f).fillMaxHeight().background(D.bg)) {
                VaultHeader(
                    category = category,
                    canGenerate = generator != null,
                    onGenerate = { showGenerate = true },
                    onAddPassword = { showAddPassword = true },
                )
                HLine()
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            !category.hasBackend -> VaultPlaceholder(category)
                            items.isEmpty() -> VaultEmptyCategory(category)
                            else -> items.forEach { identity ->
                                LiveSecretCard(
                                    identity = identity,
                                    active = identity.id == selected?.id,
                                    generator = generator,
                                    usedByCount = VaultPresentation.hostsUsing(identity.id, hosts).size,
                                    mono = mono,
                                    onClick = { selectedId = identity.id },
                                )
                            }
                        }
                    }
                    val current = selected
                    if (current != null && category.hasBackend) {
                        VLine(D.line)
                        LiveSecretDetail(
                            identity = current,
                            generator = generator,
                            hosts = hosts,
                            mono = mono,
                            onCopy = { clipboard.setText(AnnotatedString(it)) },
                            onExport = { name, content -> scope.launch { exportTextFile(name, content) } },
                            onDelete = { pendingDelete = current },
                        )
                    }
                }
            }
        }
        if (showGenerate && generator != null) {
            GenerateKeyDialog(
                onDismiss = { showGenerate = false },
                onCreate = { name, type ->
                    showGenerate = false
                    category = VaultCategoryKind.SSH_KEYS
                    // Генерация (особенно RSA-4096) дорогая — уводим с main-потока, чтобы не фризить UI;
                    // save трогает Compose-state, поэтому возвращаемся на scope (main) для записи.
                    scope.launch {
                        val key = withContext(Dispatchers.Default) { generator.generate(type, comment = name) }
                        selectedId = identities.save(
                            IdentityDraft(label = name, kind = IdentityKind.PRIVATE_KEY, privateKeyPem = key.privateKeyPem),
                        )
                    }
                },
            )
        }
        if (showAddPassword) {
            AddPasswordDialog(
                onDismiss = { showAddPassword = false },
                onCreate = { name, password ->
                    selectedId = identities.save(
                        IdentityDraft(label = name, kind = IdentityKind.PASSWORD, password = password),
                    )
                    category = VaultCategoryKind.PASSWORDS
                    showAddPassword = false
                },
            )
        }
        pendingDelete?.let { victim ->
            val bound = VaultPresentation.hostsUsing(victim.id, hosts)
            DeleteIdentityDialog(
                label = victim.label,
                boundHostCount = bound.size,
                onDismiss = { pendingDelete = null },
                onConfirm = {
                    // Сначала развязываем хосты (теряют привязку к секрету), потом удаляем identity —
                    // иначе профиль остался бы ссылаться на несуществующий id (см. IdentityStore.remove).
                    bound.forEach { host -> hostsController?.save(host.unbindIdentity()) }
                    identities.delete(victim.id)
                    if (selectedId == victim.id) selectedId = null
                    pendingDelete = null
                },
            )
        }
    }
}

private fun Host.unbindIdentity(): HostDraft =
    HostDraft(id = id, label = label, address = address, port = port, username = username, group = group, identityId = null)

// ---------------------------------------------------------------------------------------------
// Левый sidebar категорий (живые счётчики) + шапка с действием категории.
// ---------------------------------------------------------------------------------------------

@Composable
private fun VaultSidebar(active: VaultCategoryKind, identities: List<Identity>, onSelect: (VaultCategoryKind) -> Unit) {
    Column(Modifier.width(222.dp).fillMaxHeight().background(D.surface2).padding(horizontal = 8.dp, vertical = 14.dp)) {
        Txt("VAULT", color = D.faint, size = 11.sp, weight = FontWeight.SemiBold, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
        VaultCategoryKind.entries.forEach { kind ->
            VaultCategoryRow(
                icon = kind.icon,
                label = kind.title,
                count = VaultPresentation.count(kind, identities).toString(),
                active = kind == active,
                onClick = { onSelect(kind) },
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.clip(RoundedCornerShape(8.dp)).background(D.moss.copy(alpha = 0.06f)).border(1.dp, D.moss.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sym("lock", size = 15.sp, color = D.moss)
                Txt("End-to-end encrypted", color = D.moss, size = 11.sp, weight = FontWeight.SemiBold)
            }
            Txt("Private keys are sealed with your master password and never sync in plaintext.", color = D.dim, size = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun VaultCategoryRow(icon: String, label: String, count: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) D.cyan10 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Sym(icon, size = 16.sp, color = if (active) D.cyanBright else D.dim)
        Txt(label, color = if (active) D.cyanBright else D.dim, size = 12.5.sp, modifier = Modifier.weight(1f))
        Txt(count, color = D.faint, size = 10.sp)
    }
}

@Composable
private fun VaultHeader(category: VaultCategoryKind, canGenerate: Boolean, onGenerate: () -> Unit, onAddPassword: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(category.title, color = D.text, size = 15.sp, weight = FontWeight.SemiBold)
        when (category) {
            VaultCategoryKind.SSH_KEYS -> if (canGenerate) PrimaryButton("Generate key", onClick = onGenerate, icon = "add")
            VaultCategoryKind.PASSWORDS -> PrimaryButton("Add password", onClick = onAddPassword, icon = "add")
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Карточка секрета (ключ/пароль) + пустые состояния.
// ---------------------------------------------------------------------------------------------

@Composable
private fun LiveSecretCard(
    identity: Identity,
    active: Boolean,
    generator: SshKeyGenerator?,
    usedByCount: Int,
    mono: FontFamily,
    onClick: () -> Unit,
) {
    val isKey = identity.auth is IdentityAuth.PrivateKey
    val info = rememberKeyInfo(identity, generator)
    val border = if (active) D.cyan.copy(alpha = 0.18f) else D.cyan08
    val bg = if (active) D.cyan.copy(alpha = 0.04f) else Color.Transparent
    val usedBy = if (usedByCount == 1) "used by 1 host" else "used by $usedByCount hosts"
    val meta = when {
        isKey && info != null -> "${shortFingerprint(info.fingerprintSha256)} · $usedBy"
        isKey -> usedBy
        else -> "Password · $usedBy"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).border(1.dp, border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(if (isKey) D.cyan.copy(alpha = 0.12f) else Color(0x0DFFFFFF)), contentAlignment = Alignment.Center) {
            Sym(if (isKey) "key" else "password", size = 20.sp, color = if (isKey) D.cyanBright else D.dim)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(identity.label, color = D.text, size = 13.5.sp, weight = FontWeight.SemiBold)
                info?.keyTypeLabel?.let { Badge(it, bg = D.moss.copy(alpha = 0.16f), fg = D.moss, radius = 3, size = 9.5.sp) }
            }
            Txt(meta, color = D.dim, size = 11.sp, font = mono, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun VaultEmptyCategory(category: VaultCategoryKind) {
    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Sym(category.icon, size = 26.sp, color = D.faint)
            val (title, hint) = when (category) {
                VaultCategoryKind.SSH_KEYS -> "No SSH keys yet" to "Generate a key — it's sealed in your vault and ready to attach to hosts."
                VaultCategoryKind.PASSWORDS -> "No passwords yet" to "Add a password — it's stored encrypted and reusable across hosts."
                else -> "Nothing here" to ""
            }
            Txt(title, color = D.text, size = 13.sp, weight = FontWeight.SemiBold)
            if (hint.isNotEmpty()) Txt(hint, color = D.faint, size = 11.5.sp)
        }
    }
}

@Composable
private fun VaultPlaceholder(category: VaultCategoryKind) {
    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Sym(category.icon, size = 26.sp, color = D.faint)
            Txt("${category.title} — coming soon", color = D.text, size = 13.sp, weight = FontWeight.SemiBold)
            Txt("This vault category lands with a later release.", color = D.faint, size = 11.5.sp)
        }
    }
}

/** Открытые метаданные приватного ключа (отпечаток/тип/публичная строка); для пароля — null. */
@Composable
private fun rememberKeyInfo(identity: Identity, generator: SshKeyGenerator?): SshPublicKeyInfo? =
    remember(identity.id, generator) {
        (identity.auth as? IdentityAuth.PrivateKey)?.let { generator?.inspect(it.privateKeyPem, it.passphrase) }
    }

// ---------------------------------------------------------------------------------------------
// Панель деталей выбранного секрета.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveSecretDetail(
    identity: Identity,
    generator: SshKeyGenerator?,
    hosts: List<Host>,
    mono: FontFamily,
    onCopy: (String) -> Unit,
    onExport: (name: String, content: String) -> Unit,
    onDelete: () -> Unit,
) {
    val isKey = identity.auth is IdentityAuth.PrivateKey
    val info = rememberKeyInfo(identity, generator)
    val bound = VaultPresentation.hostsUsing(identity.id, hosts)
    Column(Modifier.width(340.dp).fillMaxHeight().background(D.surface2).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(if (isKey) D.cyan.copy(alpha = 0.12f) else Color(0x0DFFFFFF)), contentAlignment = Alignment.Center) {
                Sym(if (isKey) "key" else "password", size = 21.sp, color = if (isKey) D.cyanBright else D.dim)
            }
            Column {
                Txt(identity.label, color = D.text, size = 14.sp, weight = FontWeight.SemiBold)
                Txt(info?.keyTypeLabel ?: if (isKey) "Private key" else "Password", color = D.dim, size = 11.5.sp)
            }
        }
        if (isKey) {
            DetailLabel("Public key")
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(7.dp)).background(D.terminalBg).border(1.dp, D.cyan.copy(alpha = 0.1f), RoundedCornerShape(7.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Txt(info?.publicKeyOpenSsh ?: "Key could not be read", color = D.dim, size = 10.5.sp, font = mono, lineHeight = 16.sp)
            }
            DetailLabel("Fingerprint")
            Txt(info?.fingerprintSha256 ?: "—", color = D.textBright, size = 11.sp, font = mono, modifier = Modifier.padding(bottom = 16.dp))
        }
        DetailLabel("Used by · ${bound.size} ${if (bound.size == 1) "host" else "hosts"}")
        if (bound.isEmpty()) {
            Txt("Not attached to any host yet.", color = D.faint, size = 11.sp, modifier = Modifier.padding(bottom = 20.dp))
        } else {
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bound.forEach { HostPill(it.label, mono) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isKey) {
                PrimaryButton("Copy public key", onClick = { info?.let { onCopy(it.publicKeyOpenSsh) } }, icon = "content_copy", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Export", onClick = { info?.let { onExport("${identity.label}.pub", it.publicKeyOpenSsh) } }, modifier = Modifier.weight(1f))
                    GhostButton("Delete", onClick = onDelete, fg = D.sunset, border = D.sunset.copy(alpha = 0.3f), modifier = Modifier.weight(1f))
                }
            } else {
                PrimaryButton("Copy password", onClick = { onCopy((identity.auth as IdentityAuth.Password).password) }, icon = "content_copy", modifier = Modifier.fillMaxWidth())
                GhostButton("Delete", onClick = onDelete, fg = D.sunset, border = D.sunset.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Диалоги: генерация ключа, добавление пароля, подтверждение удаления.
// ---------------------------------------------------------------------------------------------

@Composable
private fun GenerateKeyDialog(onDismiss: () -> Unit, onCreate: (name: String, type: SshKeyType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SshKeyType.ED25519) }
    val valid = name.isNotBlank()
    VaultDialogScaffold("Generate SSH key", "A new key pair is sealed in your vault — no passphrase needed.", onDismiss) {
        DialogField("NAME", name, { name = it }, placeholder = "e.g. work-laptop")
        Txt("ALGORITHM", color = D.faint, size = 10.5.sp, weight = FontWeight.SemiBold, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SshKeyType.entries.forEach { option ->
                Chip(option.label, active = option == type, modifier = Modifier.clickable { type = option })
            }
        }
        DialogButtons(confirmLabel = "Generate", confirmEnabled = valid, onDismiss = onDismiss, onConfirm = { onCreate(name.trim(), type) })
    }
}

@Composable
private fun AddPasswordDialog(onDismiss: () -> Unit, onCreate: (name: String, password: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && password.isNotEmpty()
    VaultDialogScaffold("Add password", "Stored encrypted in your vault and reusable across hosts.", onDismiss) {
        DialogField("NAME", name, { name = it }, placeholder = "e.g. db-admin")
        Box(Modifier.padding(top = 16.dp)) {
            DialogField("PASSWORD", password, { password = it }, placeholder = "secret", password = true)
        }
        DialogButtons(confirmLabel = "Add", confirmEnabled = valid, onDismiss = onDismiss, onConfirm = { onCreate(name.trim(), password) })
    }
}

@Composable
private fun DeleteIdentityDialog(label: String, boundHostCount: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    VaultDialogScaffold("Delete \"$label\"?", null, onDismiss) {
        val detail = if (boundHostCount == 0) {
            "This secret is removed from your vault. This can't be undone."
        } else {
            "$boundHostCount ${if (boundHostCount == 1) "host" else "hosts"} will lose this credential and ask for a password on next connect. This can't be undone."
        }
        Txt(detail, color = D.dim, size = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 9.dp)) {
                Txt("Cancel", color = D.dim, size = 12.5.sp)
            }
            PrimaryButton("Delete", onClick = onConfirm, bg = D.sunset, fg = Color(0xFF1A0B07))
        }
    }
}

@Composable
private fun VaultDialogScaffold(title: String, subtitle: String?, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB3060E16)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(D.surfaceDeep)
                .border(1.dp, D.cyan14, RoundedCornerShape(12.dp))
                // Гасим клик по карточке, чтобы он не закрывал диалог (как в DesktopPasswordDialog).
                .clickable(onClick = {})
                .padding(26.dp),
        ) {
            Txt(title, color = D.text, size = 16.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
            if (subtitle != null) Txt(subtitle, color = D.dim, size = 12.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            else Spacer(Modifier.padding(top = 8.dp))
            content()
        }
    }
}

@Composable
private fun DialogField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, password: Boolean = false) {
    val ui = LocalFonts.current.ui
    val style = remember(ui) { TextStyle(color = D.text, fontSize = 13.sp, fontFamily = ui) }
    Column {
        Txt(label, color = D.faint, size = 10.5.sp, weight = FontWeight.SemiBold, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 5.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(D.bg).border(1.dp, D.cyan14, RoundedCornerShape(7.dp)).padding(horizontal = 11.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) Txt(placeholder, color = D.faint, size = 13.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(D.cyan),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
                keyboardActions = KeyboardActions(),
            )
        }
    }
}

@Composable
private fun DialogButtons(confirmLabel: String, confirmEnabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 9.dp)) {
            Txt("Cancel", color = D.dim, size = 12.5.sp)
        }
        PrimaryButton(confirmLabel, onClick = { if (confirmEnabled) onConfirm() }, bg = if (confirmEnabled) D.cyan else D.cyan.copy(alpha = 0.4f))
    }
}

// ---------------------------------------------------------------------------------------------
// Мок-путь (офскрин-рендер/превью): статичные категории/ключи/детали из макета.
// ---------------------------------------------------------------------------------------------

/** Vault view (мок): категории секретов (sidebar) + список SSH-ключей + панель деталей ключа. */
@Composable
private fun MockVaultView() {
    val mono = LocalFonts.current.mono
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(222.dp).fillMaxHeight().background(D.surface2).padding(horizontal = 8.dp, vertical = 14.dp)) {
            Txt("VAULT", color = D.faint, size = 11.sp, weight = FontWeight.SemiBold, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
            VaultCategory("key", "SSH keys", "4", active = true)
            VaultCategory("badge", "Identities", "3")
            VaultCategory("password", "Passwords", "12")
            VaultCategory("vpn_lock", "Certificates", "2")
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).background(D.moss.copy(alpha = 0.06f)).border(1.dp, D.moss.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sym("lock", size = 15.sp, color = D.moss)
                    Txt("End-to-end encrypted", color = D.moss, size = 11.sp, weight = FontWeight.SemiBold)
                }
                Txt("Private keys are sealed with your master password and never sync in plaintext.", color = D.dim, size = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        VLine(D.line)
        Column(Modifier.weight(1f).fillMaxHeight().background(D.bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt("SSH keys", color = D.text, size = 15.sp, weight = FontWeight.SemiBold)
                PrimaryButton("Generate key", onClick = {}, icon = "add")
            }
            HLine()
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyCard(
                        iconBg = D.cyan.copy(alpha = 0.12f), iconColor = D.cyanBright, icon = "key",
                        name = "id_ed25519", badges = listOf("ED25519" to false, "DEFAULT" to true),
                        meta = "SHA256:8c3F1a…Qz9pK · used by 6 hosts", mono = mono,
                        border = D.cyan.copy(alpha = 0.18f), bg = D.cyan.copy(alpha = 0.04f),
                        trailing = { CopyButton() },
                    )
                    KeyCard(
                        iconBg = Color(0x0DFFFFFF), iconColor = D.dim, icon = "key",
                        name = "id_rsa_legacy", badges = listOf("RSA-4096" to null),
                        meta = "SHA256:2dE7b…Lm4xR · used by 2 hosts", mono = mono,
                        border = D.cyan08, bg = Color.Transparent,
                        trailing = { CopyButton() },
                    )
                    KeyCard(
                        iconBg = D.sunset.copy(alpha = 0.12f), iconColor = D.sunset, icon = "warning",
                        name = "deploy_ci", badges = listOf("ROTATE SOON" to false), rotateBadge = true,
                        meta = "SHA256:9aB0c…Tn2wE · created 412 days ago", mono = mono,
                        border = D.sunset.copy(alpha = 0.25f), bg = D.sunset.copy(alpha = 0.04f),
                        trailing = {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, D.sunset.copy(alpha = 0.4f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                                Txt("Rotate", color = D.sunset, size = 11.5.sp, weight = FontWeight.SemiBold)
                            }
                        },
                    )
                }
                VLine(D.line)
                KeyDetail(mono)
            }
        }
    }
}

@Composable
private fun VaultCategory(icon: String, label: String, count: String, active: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) D.cyan10 else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Sym(icon, size = 16.sp, color = if (active) D.cyanBright else D.dim)
        Txt(label, color = if (active) D.cyanBright else D.dim, size = 12.5.sp, modifier = Modifier.weight(1f))
        Txt(count, color = D.faint, size = 10.sp)
    }
}

@Composable
private fun KeyCard(
    iconBg: Color,
    iconColor: Color,
    icon: String,
    name: String,
    badges: List<Pair<String, Boolean?>>,
    meta: String,
    mono: FontFamily,
    border: Color,
    bg: Color,
    rotateBadge: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).border(1.dp, border, RoundedCornerShape(10.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Sym(icon, size = 20.sp, color = iconColor)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(name, color = D.text, size = 13.5.sp, weight = FontWeight.SemiBold)
                badges.forEach { (text, default) ->
                    when {
                        rotateBadge -> Badge(text, bg = D.sunset.copy(alpha = 0.16f), fg = D.sunset, radius = 3, size = 9.5.sp)
                        default == true -> Badge(text, bg = D.cyan14, fg = D.cyanBright, radius = 3, size = 9.5.sp)
                        default == false -> Badge(text, bg = D.moss.copy(alpha = 0.16f), fg = D.moss, radius = 3, size = 9.5.sp)
                        else -> Badge(text, bg = Color(0x0FFFFFFF), fg = D.dim, radius = 3, size = 9.5.sp)
                    }
                }
            }
            Txt(meta, color = D.dim, size = 11.sp, font = mono, modifier = Modifier.padding(top = 6.dp))
        }
        trailing()
    }
}

@Composable
private fun CopyButton() {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, D.cyan14, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        Sym("content_copy", size = 16.sp, color = D.dim)
    }
}

@Composable
private fun KeyDetail(mono: FontFamily) {
    Column(Modifier.width(340.dp).fillMaxHeight().background(D.surface2).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(D.cyan.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Sym("key", size = 21.sp, color = D.cyanBright)
            }
            Column {
                Txt("id_ed25519", color = D.text, size = 14.sp, weight = FontWeight.SemiBold)
                Txt("ED25519 · 256-bit", color = D.dim, size = 11.5.sp)
            }
        }
        DetailLabel("Public key")
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(7.dp)).background(D.terminalBg).border(1.dp, D.cyan.copy(alpha = 0.1f), RoundedCornerShape(7.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Txt("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIH8c3F1a2bQz9pK7mLwR0vNqz9pKmaya@skerry.dev", color = D.dim, size = 10.5.sp, font = mono, lineHeight = 16.sp)
        }
        DetailLabel("Fingerprint")
        Txt("SHA256:8c3F1a2bQz9pK7mLwR0vNqz9pK", color = D.textBright, size = 11.sp, font = mono, modifier = Modifier.padding(bottom = 16.dp))
        DetailLabel("Used by · 6 hosts")
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HostPill("prod-web-01", mono)
            HostPill("prod-web-02", mono)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HostPill("db-master", mono)
            HostPill("homelab-pi", mono)
            HostPill("+2", mono, dim = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Copy public key", onClick = {}, icon = "content_copy", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Export", onClick = {}, modifier = Modifier.weight(1f))
                GhostButton("Delete", onClick = {}, fg = D.sunset, border = D.sunset.copy(alpha = 0.3f), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailLabel(text: String) {
    Txt(text.uppercase(), color = D.faint, size = 10.sp, weight = FontWeight.SemiBold, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun HostPill(name: String, mono: FontFamily, dim: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x0AFFFFFF)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Txt(name, color = if (dim) D.dim else D.textBright, size = 11.sp, font = mono)
    }
}
