package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsChoice
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsOption
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SenderKind
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Send-as for previews and screenshot tests: a group where the user can post as themselves, as two channels they own
 * (one needing Telegram Premium) or anonymously, sending as a channel now.
 */
internal object SendAsPreviewData {
    private const val ME = 1L
    private const val NEWS = -200L
    private const val LAB = -300L

    /** The group of [ChatPreviewData.groupChat]. */
    private val GROUP = checkNotNull(ChatPreviewData.groupChat.header).chatId

    val options =
        persistentListOf(
            option(MessageSender.User(ME), "Ada Lovelace", SenderKind.Account),
            option(MessageSender.Chat(NEWS), "Engine news", SenderKind.Channel, selected = true),
            option(MessageSender.Chat(LAB), "Difference lab", SenderKind.Channel, needsPremium = true),
            option(MessageSender.Chat(GROUP), "Compose Developers", SenderKind.Anonymous),
        )

    /** The picker with every sender, a channel chosen. */
    val sheet = SendAsSheet(options)

    /** The picker while Telegram is asked. */
    val loading = SendAsSheet()

    /** The picker while the user's own account, just tapped, is on its way to TDLib. */
    val changing =
        SendAsSheet(
            options.map { it.copy(selected = it.kind == SenderKind.Account) }.toImmutableList(),
            isChanging = true,
        )

    /** The composer's avatar while sending as "Engine news". */
    val choice = SendAsChoice(NEWS, UiText.Plain("Engine news"), photo = null)

    /** The group conversation, its composer sending as a channel. */
    val groupChat: ChatUiState =
        ChatPreviewData.groupChat.copy(
            composer = (ChatPreviewData.groupChat.composer as ComposerUiState.Available).copy(sendAs = choice),
        )

    private fun option(
        sender: MessageSender,
        name: String,
        kind: SenderKind,
        selected: Boolean = false,
        needsPremium: Boolean = false,
    ) = SendAsOption(
        sender = sender,
        id =
            when (sender) {
                is MessageSender.User -> sender.userId
                is MessageSender.Chat -> sender.chatId
            },
        name = UiText.Plain(name),
        photo = null,
        kind = kind,
        selected = selected,
        needsPremium = needsPremium,
    )
}
