package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.domain.model.ConferenceChannel
import dev.kyralabs.pingogram.core.domain.repository.ConferenceCallRepository
import dev.kyralabs.pingogram.feature.calls.engine.ConferenceEncryption
import kotlinx.coroutines.runBlocking

/**
 * A conference call's frames encrypted by TDLib (`docs/adr/0025-conference-calls.md`): each one waits for TDLib's
 * answer on the engine's thread that asks, as the engine must have it before it goes on. Until the call is joined
 * ([groupCallId] still 0) there is nothing to encrypt for, and frames are dropped.
 */
internal class TdlibConferenceEncryption(
    private val conferences: ConferenceCallRepository,
) : ConferenceEncryption {
    /** TDLib's id of the call, set once its join answered. */
    @Volatile var groupCallId = 0

    override fun encrypt(
        channel: ConferenceChannel,
        data: ByteArray,
        unencryptedPrefix: Int,
    ): ByteArray? {
        val id = groupCallId.takeIf { it != 0 } ?: return null
        return runBlocking { conferences.encrypt(id, channel, data, unencryptedPrefix) }
    }

    override fun decrypt(
        userId: Long,
        data: ByteArray,
    ): ByteArray? {
        val id = groupCallId.takeIf { it != 0 } ?: return null
        return runBlocking { conferences.decrypt(id, userId, data) }
    }
}
