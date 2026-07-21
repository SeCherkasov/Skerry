package app.skerry.shared.agent

/**
 * One vault secret offered to the agent, flattened to what a keyring needs. Built by the UI layer
 * from [app.skerry.shared.vault.Credential] (the `shared` core does not reach into the vault
 * itself), so the agent has no opinion on where keys are stored or which of them the user picked.
 *
 * [id] is the credential id — the cache key for an already-parsed key. [comment] is what
 * `ssh-add -l` shows on the remote side, i.e. the credential's label; it is user-visible on the
 * far end of a forwarded agent, so it must stay a label and never carry the secret. [certificate]
 * is the `*-cert.pub` string when the secret is a certificate: the agent then offers the
 * certificate blob and signs with the private key behind it.
 *
 * `toString` is redacted like every secret-bearing type in the vault.
 */
class SshAgentKeyMaterial(
    val id: String,
    val comment: String,
    val privateKeyPem: String,
    val passphrase: String? = null,
    val certificate: String? = null,
) {
    override fun toString(): String = "SshAgentKeyMaterial(id=$id, secrets=redacted)"
}
