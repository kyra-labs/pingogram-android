package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.EncryptionKeyHash
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.secret.SELF_DESTRUCT_CHOICES
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatSheet
import dev.kyralabs.pingogram.feature.chat.ui.secret.TimerOption
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Sample secret chats with Ada (`REQUIREMENTS.md` FR-E1) for previews and screenshot tests: ready, with a 30-second
 * self-destruct timer on the messages sent since and the banner above the first; waiting for Ada, with no messages
 * yet; ended. The self-destruct timer sheet, also while a choice goes to TDLib, and the encryption key sheet, also
 * before the key is agreed.
 */
internal object SecretChatPreviewData {
    private val ada = UiText.Plain("Ada")

    /** A key hash with every kind of pixel, in a pattern that is not regular. */
    val keyHash =
        EncryptionKeyHash(ByteArray(KEY_HASH_BYTES) { (it * it * KEY_HASH_STEP + it * KEY_HASH_OFFSET).toByte() })

    private fun header(
        state: SecretChatState,
        subtitle: UiText,
        timer: Int = 0,
    ) = ChatHeader(
        chatId = ChatPreviewData.ADA,
        title = UiText.Plain("Ada Lovelace"),
        photo = null,
        placeholderIcon = null,
        subtitle = ChatSubtitle.Status(subtitle),
        secret = SecretChatHeader(state, timer),
    )

    val ready =
        ChatUiState(
            header = header(SecretChatState.Ready, UiText.resource(UiR.string.user_status_online), timer = TIMER),
            composer = ComposerUiState.Available(canSchedule = false),
            content =
                ChatPreviewData.content(
                    ChatPreviewData
                        .bubble(
                            3,
                            "Only on this phone, then.",
                            time = "9:42 AM",
                            isOutgoing = true,
                            status = OutgoingStatus.Read,
                        ).copy(selfDestructSeconds = TIMER),
                    ChatPreviewData
                        .bubble(
                            2,
                            "This one deletes itself.",
                            time = "9:41 AM",
                            sender = ada,
                            senderId = ChatPreviewData.ADA,
                            position = GroupPosition.Single,
                        ).copy(selfDestructSeconds = TIMER),
                    ChatPreviewData.bubble(
                        1,
                        "Hi Ada!",
                        time = "9:40 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                    ),
                ),
        )

    val pending =
        ChatUiState(
            header =
                header(
                    SecretChatState.Pending,
                    UiText.resource(UiR.string.secret_chat_waiting, ada.bidiIsolated()),
                ),
            composer = ComposerUiState.Unavailable(UiText.resource(R.string.composer_secret_pending)),
            content = ChatContent.Empty,
        )

    val closed =
        ready.copy(
            header = header(SecretChatState.Closed, UiText.resource(UiR.string.secret_chat_ended), timer = TIMER),
            composer = ComposerUiState.Unavailable(UiText.resource(R.string.composer_secret_closed)),
        )

    val timer =
        SecretChatSheet.Timer(
            options = SELF_DESTRUCT_CHOICES.map { TimerOption(it, selected = it == TIMER) }.toImmutableList(),
        )

    val timerChanging =
        SecretChatSheet.Timer(
            options = SELF_DESTRUCT_CHOICES.map { TimerOption(it, selected = it == CHANGING_TO) }.toImmutableList(),
            isChanging = true,
        )

    val key = SecretChatSheet.Key(name = ada, keyHash = keyHash, canEnd = true)

    val keyPending = SecretChatSheet.Key(name = ada, keyHash = EncryptionKeyHash.Empty, canEnd = true)

    private const val TIMER = 30
    private const val CHANGING_TO = 60
    private const val KEY_HASH_BYTES = 36
    private const val KEY_HASH_STEP = 37
    private const val KEY_HASH_OFFSET = 91
}
