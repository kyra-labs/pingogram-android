package dev.kyralabs.pingogram.core.domain.model

/**
 * A passkey that may sign in to the user's account (td_api.tl `passkey`, `docs/adr/0037-passkeys.md`): Telegram's [id]
 * of it, its [name] (the password manager that keeps it, as Telegram names it), when it was added and last used (Unix
 * seconds, [lastUsedAt] 0 if never). Pingogram never sees the passkey's key: the user's password manager keeps it.
 */
data class Passkey(
    val id: String,
    val name: String,
    val addedAt: Long,
    val lastUsedAt: Long,
)

/**
 * What the user's password manager answered for a new passkey, as TDLib's `addLoginPasskey` takes it: the client data as
 * its JSON text ([clientData]) and the authenticator's attestation object.
 */
class PasskeyRegistration(
    val clientData: String,
    val attestationObject: ByteArray,
)
