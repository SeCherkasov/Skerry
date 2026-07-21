package app.skerry.ui.agent

import app.skerry.shared.agent.SshAgentAction
import app.skerry.shared.agent.SshAgentActivityLog
import app.skerry.shared.agent.SshAgentOrigin
import app.skerry.shared.agent.SshAgentUsage
import app.skerry.shared.vault.Credential
import app.skerry.shared.vault.CredentialSecret
import app.skerry.shared.vault.CredentialStore
import app.skerry.shared.vault.DataKey
import app.skerry.shared.vault.MergeResult
import app.skerry.shared.vault.RecordType
import app.skerry.shared.vault.SyncMeta
import app.skerry.shared.vault.UnlockResult
import app.skerry.shared.vault.Vault
import app.skerry.shared.vault.VaultRecord
import app.skerry.ui.identity.CredentialManagerController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSocket(var failing: Boolean = false) : SshAgentSocket {
    override val isSupported = true
    var running = false
        private set

    override fun start(): String? {
        if (failing) return null
        running = true
        return "/run/skerry/agent.sock"
    }

    override fun stop() {
        running = false
    }
}

class SshAgentControllerTest {

    private val vault = FakeAgentVault()
    private val credentials = CredentialManagerController(CredentialStore(vault)) { "generated" }
    private var parsedKeysDropped = 0

    private fun controller(socket: SshAgentSocket? = null, enabled: Boolean = true) =
        SshAgentController(
            credentials = credentials,
            isVaultUnlocked = { vault.isUnlocked },
            socket = socket,
            dropParsedKeys = { parsedKeysDropped++ },
            activityLog = SshAgentActivityLog(clock = { "2026-07-21T10:00:00Z" }),
            initialEnabled = enabled,
            initialSocketEnabled = false,
        )

    private fun addKey(id: String, label: String, inAgent: Boolean, secret: CredentialSecret = CredentialSecret.PrivateKey("pem-$id")) {
        CredentialStore(vault).put(Credential(id = id, label = label, secret = secret, agentEnabled = inAgent))
        credentials.reload()
    }

    @Test
    fun `offers only the keys the user put in the agent`() {
        addKey("a", "work", inAgent = true)
        addKey("b", "home", inAgent = false)

        assertEquals(listOf("work"), controller().keyMaterial().map { it.comment })
    }

    @Test
    fun `passwords are never offered as agent keys`() {
        // A password is not a key; it must not show up in the agent's list, enabled flag or not.
        addKey("p", "server password", inAgent = true, secret = CredentialSecret.Password("s3cret"))

        assertTrue(controller().keyMaterial().isEmpty())
        assertTrue(controller().agentKeys.isEmpty())
    }

    @Test
    fun `certificates are offered with their certificate blob`() {
        addKey("c", "ca key", inAgent = true, secret = CredentialSecret.Certificate("pem", "ssh-ed25519-cert-v01@openssh.com AAAA"))

        val material = controller().keyMaterial().single()
        assertEquals("ssh-ed25519-cert-v01@openssh.com AAAA", material.certificate)
    }

    @Test
    fun `a disabled agent offers nothing`() {
        addKey("a", "work", inAgent = true)

        assertTrue(controller(enabled = false).keyMaterial().isEmpty())
    }

    @Test
    fun `a locked vault offers nothing`() {
        // Terminal sessions survive a lock, so a forwarded channel can still ask — and must get
        // nothing until the user unlocks again.
        addKey("a", "work", inAgent = true)
        val controller = controller()
        vault.lock()

        assertTrue(controller.keyMaterial().isEmpty())
    }

    @Test
    fun `switching the agent off drops parsed keys and stops the socket`() {
        val socket = FakeSocket()
        val controller = controller(socket)
        controller.exposeSocket(true)
        assertTrue(socket.running)

        controller.enable(false)

        assertFalse(socket.running)
        assertTrue(parsedKeysDropped > 0)
        assertNull(controller.socketPath)
    }

    @Test
    fun `changing which keys are in the agent re-parses them`() {
        addKey("a", "work", inAgent = false)
        val controller = controller()
        val before = parsedKeysDropped

        controller.setKeyInAgent("a", true)

        assertEquals(listOf("work"), controller.keyMaterial().map { it.comment })
        assertTrue(parsedKeysDropped > before, "parsed keys must be dropped when the key set changes")
    }

    @Test
    fun `the socket is only started while the agent is on`() {
        val socket = FakeSocket()
        val controller = controller(socket, enabled = false)

        controller.exposeSocket(true)

        assertFalse(socket.running, "socket exposed keys while the agent was off")
        assertNull(controller.socketPath)
    }

    @Test
    fun `a socket that cannot be bound is reported instead of failing silently`() {
        val socket = FakeSocket(failing = true)
        val controller = controller(socket)

        controller.exposeSocket(true)

        assertTrue(controller.socketFailed)
        assertNull(controller.socketPath)
    }

    @Test
    fun `locking the vault stops the socket and unlocking brings it back`() {
        val socket = FakeSocket()
        val controller = controller(socket)
        controller.exposeSocket(true)

        controller.onVaultLocked()
        assertFalse(socket.running)
        assertTrue(parsedKeysDropped > 0)

        controller.onVaultUnlocked()
        assertTrue(socket.running)
    }

    @Test
    fun `records agent use newest first and keeps the list bounded`() {
        val controller = controller()
        repeat(MAX + 5) {
            controller.record(SshAgentUsage(SshAgentOrigin.Session("host-$it"), SshAgentAction.Signed, "work"))
        }

        assertEquals(MAX, controller.activity.size)
        assertEquals("host-${MAX + 4}", (controller.activity.first().origin as SshAgentOrigin.Session).address)
        assertEquals("2026-07-21T10:00:00Z", controller.activity.first().at)
    }

    private companion object {
        const val MAX = SshAgentActivityLog.DEFAULT_MAX
    }
}

/** In-memory vault that can actually lock — the agent's key access hangs off that. */
private class FakeAgentVault : Vault {
    private val payloads = mutableMapOf<String, ByteArray>()
    private val records = mutableMapOf<String, VaultRecord>()

    override var isUnlocked: Boolean = true
        private set

    override fun exists(): Boolean = true
    override fun create(password: CharArray) { isUnlocked = true }
    override fun unlock(password: CharArray): UnlockResult { isUnlocked = true; return UnlockResult.Success }
    override fun lock() { isUnlocked = false }
    override fun reset() { payloads.clear(); records.clear() }

    override fun records(): List<VaultRecord> = records.values.toList()
    override fun syncMeta(): SyncMeta? = null
    override fun mergeRemote(remote: List<VaultRecord>): MergeResult = MergeResult.EMPTY
    override fun openPayload(id: String): ByteArray? = records[id]?.takeIf { !it.deleted }?.let { payloads[id] }

    override fun put(id: String, type: RecordType, payload: ByteArray) {
        val version = (records[id]?.version ?: 0L) + 1
        records[id] = VaultRecord(id, type, version, "2026-07-21T00:00:00Z", "dev", deleted = false, blob = ByteArray(0))
        payloads[id] = payload
    }

    override fun remove(id: String) {
        records[id] = (records[id] ?: return).copy(version = records[id]!!.version + 1, deleted = true)
    }

    override fun changePassword(oldPassword: CharArray, newPassword: CharArray): Boolean = true
    override fun verifyPassword(password: CharArray): Boolean = true
    override fun unlockWithDataKey(dataKey: DataKey): UnlockResult = UnlockResult.Corrupted
    override fun exportDataKey(): DataKey? = null
    override fun adoptDataKey(newDataKey: DataKey, password: CharArray): Boolean = false
}
