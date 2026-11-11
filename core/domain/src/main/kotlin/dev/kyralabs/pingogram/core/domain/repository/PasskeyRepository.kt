package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Passkey
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import kotlinx.collections.immutable.ImmutableList

/**
 * The passkeys that may sign in to the user's account (Privacy and security → Passkeys, `docs/adr/0037-passkeys.md`).
 * Nothing is cached: each call asks Telegram through TDLib, which announces no changes to them. Creating the passkey
 * is the user's password manager's, through Android's Credential Manager: [creationOptions] gives it Telegram's request
 * and [add] hands its answer back.
 */
interface PasskeyRepository {
    /** The account's passkeys, in Telegram's order. */
    suspend fun passkeys(): Outcome<ImmutableList<Passkey>>

    /** Telegram's options for a new passkey, the WebAuthn creation request as JSON, for the password manager. */
    suspend fun creationOptions(): Outcome<String>

    /** Adds the passkey the password manager made from [creationOptions]' request, returning it. */
    suspend fun add(registration: PasskeyRegistration): Outcome<Passkey>

    /** Removes passkey [passkeyId], so it no longer signs in to the account. */
    suspend fun remove(passkeyId: String): Outcome<Unit>
}
