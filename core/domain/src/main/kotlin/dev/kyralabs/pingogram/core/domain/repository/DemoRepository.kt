package dev.kyralabs.pingogram.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * The demo for the app stores' reviewers (`REQUIREMENTS.md` FR-A9, `docs/adr/0034-demo-mode-for-store-review.md`):
 * sample chats stored on the phone, without a Telegram account or the network. While it is on, the screens show the
 * demo's graph instead of the accounts'; after entering or leaving, the app opens again so no screen keeps the other.
 */
interface DemoRepository {
    /** Whether the demo shows; false until the accounts are read as the app starts. */
    val isDemo: StateFlow<Boolean>

    /** Turns the demo on, and keeps it on at the next start until [leave]. */
    suspend fun enter()

    /** Turns the demo off and deletes everything it made (what the user sent, read, reacted to). */
    suspend fun leave()
}
