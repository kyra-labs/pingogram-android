package dev.kyralabs.pingogram.feature.calls.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.calls.CallClock
import dev.kyralabs.pingogram.feature.calls.CallTones
import dev.kyralabs.pingogram.feature.calls.CallerNames
import dev.kyralabs.pingogram.feature.calls.ChatTitles
import dev.kyralabs.pingogram.feature.calls.R
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The caller's tones from the platform's [ToneGenerator] on the voice call stream, so they come from where the call's
 * sound will: the ringback tone while the other person's phone rings, the busy tone when they decline. No sound files
 * are bundled (CLAUDE.md rule 9).
 */
@Singleton
internal class ToneGeneratorCallTones
    @Inject
    constructor() : CallTones {
        private var generator: ToneGenerator? = null

        override fun ringback() = play(ToneGenerator.TONE_SUP_RINGTONE, durationMs = -1)

        override fun busy() = play(ToneGenerator.TONE_SUP_BUSY, BUSY_MS)

        @Synchronized
        override fun stop() {
            generator?.release()
            generator = null
        }

        @Synchronized
        private fun play(
            tone: Int,
            durationMs: Int,
        ) {
            stop()
            generator =
                try {
                    ToneGenerator(AudioManager.STREAM_VOICE_CALL, VOLUME).also { it.startTone(tone, durationMs) }
                } catch (
                    // ToneGenerator throws nothing narrower.
                    @Suppress("TooGenericExceptionCaught") e: RuntimeException,
                ) {
                    // The audio system may refuse a tone generator (none free, no audio device): no tone then.
                    Log.w("CallTones", "No tone: ${e.javaClass.simpleName}")
                    null
                }
        }

        private companion object {
            const val VOLUME = 80
            const val BUSY_MS = 2_000
        }
    }

internal object SystemCallClock : CallClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

/** The user's name as the app shows it; "Someone" while TDLib does not know them. */
internal class UserCallerNames
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val userRepository: UserRepository,
    ) : CallerNames {
        override suspend fun name(userId: Long): String {
            val user = userRepository.user(userId).first() ?: userRepository.getUser(userId).getOrNull()
            return user?.fullName()?.resolve(context.resources)?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.call_someone)
        }
    }

/** A chat's title from [ChatRepository]; "Voice chat" while TDLib has not announced the chat. */
internal class RepositoryChatTitles
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val chatRepository: ChatRepository,
    ) : ChatTitles {
        override suspend fun title(chatId: Long): String =
            chatRepository
                .chat(chatId)
                .first()
                ?.title
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.voice_chat)
    }
