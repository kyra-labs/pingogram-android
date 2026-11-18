package dev.kyralabs.pingogram.feature.chat.ui.player

import android.os.Handler
import android.os.Looper
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Whether voice messages skip their silences (`REQUIREMENTS.md` FR-X4, Settings → Appearance → Chats), for
 * [ExoAudioPlayer]: the setting is read only from the first voice message on ([follow]), as raise to listen's
 * (`docs/audit/performance-review-2026-11.md`, W5), and each change is told on the main thread.
 */
internal class SkipSilence
    @Inject
    constructor(
        private val settings: PlaybackSettingsRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val handler = Handler(Looper.getMainLooper())

        private var job: Job? = null

        /** Whether silences are skipped, as last read: false until then. The player's thread reads it too. */
        @Volatile
        var enabled = false
            private set

        /** Follows the setting from now on, once: [onChange] gets each value on the main thread, the first included. */
        fun follow(onChange: (Boolean) -> Unit) {
            if (job != null) return
            job =
                scope.launch {
                    settings.skipSilence.collect { skip ->
                        handler.post {
                            enabled = skip
                            onChange(skip)
                        }
                    }
                }
        }

        /** Stops following the setting; the player is gone for good. */
        fun release() {
            job?.cancel()
            job = null
        }
    }
