package dev.kyralabs.pingogram.feature.chat.ui.secret

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.EncryptionKeyHash
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * What the top bar shows of a secret chat (`REQUIREMENTS.md` FR-E1): the lock before the name, the [state] (the
 * subtitle says when the chat waits or ended), and the self-destruct timer ([selfDestructSeconds], 0 for off) on its
 * button, which works only while the chat is [SecretChatState.Ready].
 */
@Immutable
data class SecretChatHeader(
    val state: SecretChatState,
    val selfDestructSeconds: Int,
) {
    /** The timer can be changed: only once the key is agreed, and until the chat ends. */
    val canSetTimer: Boolean get() = state == SecretChatState.Ready
}

/** A sheet of a secret chat over the conversation; kept out of `ChatUiState`, like the send-as picker. */
@Immutable
sealed interface SecretChatSheet {
    /**
     * The self-destruct timer: the [options] with the chosen one marked; [isChanging] while a new choice is on its way
     * to TDLib, which shows it chosen meanwhile.
     */
    data class Timer(
        val options: ImmutableList<TimerOption>,
        val isChanging: Boolean = false,
    ) : SecretChatSheet

    /**
     * The encryption key, which both people compare: its [keyHash] as an image and as hexadecimal (empty while the
     * chat waits for the other user, [name]); "End secret chat" where [canEnd], behind a confirmation while
     * [confirmingEnd].
     */
    data class Key(
        val name: UiText,
        val keyHash: EncryptionKeyHash,
        val canEnd: Boolean,
        val confirmingEnd: Boolean = false,
    ) : SecretChatSheet {
        override fun toString() = "Key(canEnd=$canEnd, confirmingEnd=$confirmingEnd, …)"
    }
}

/** One timer in the sheet: [seconds] (0 for off), [selected] the chat's. */
@Immutable
data class TimerOption(
    val seconds: Int,
    val selected: Boolean,
)
