package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * What the composer's attach menu offers (`REQUIREMENTS.md` FR-C5), in its order. The recent photos would need media
 * permissions the app does not ask for (`REQUIREMENTS.md` §7): the Photo Picker serves instead.
 */
enum class AttachOption {
    /** Photos and videos from the system's Photo Picker, sent as photos and videos (an album). */
    Gallery,

    /** A photo from the camera app. */
    Camera,

    /** Any files from the system's document picker, sent as documents. */
    File,

    /** The device's location, found and sent in a sheet of its own (`location/ShareLocationSheet`). */
    Location,

    /** A phone number from the system's contact picker. */
    Contact,

    /** A poll or quiz, created in a form. */
    Poll,
}

/**
 * The attach options of [chat], for a user who may write in it: everything everywhere, but polls only where Telegram
 * takes them (td/telegram/MessageContent.cpp, can_send_message_content): groups and channels, chats with bots and the
 * user's Saved Messages, never secret chats. [user] is the other user of a private chat (null while unknown), and
 * [myUserId] the current user's (null while unknown).
 */
internal fun attachOptions(
    chat: Chat,
    user: User?,
    myUserId: Long?,
): ImmutableList<AttachOption> {
    val canSendPolls =
        when (val type = chat.type) {
            is ChatType.BasicGroup, is ChatType.Supergroup -> true
            is ChatType.Private -> user?.type == UserType.Bot || (myUserId != null && type.userId == myUserId)
            is ChatType.Secret -> false
        }
    return AttachOption.entries.filter { it != AttachOption.Poll || canSendPolls }.toImmutableList()
}

/** Whether [chat] is a channel, where polls are always anonymous (td_api.tl `inputMessagePoll.is_anonymous`). */
internal val Chat.isChannel: Boolean get() = (type as? ChatType.Supergroup)?.isChannel == true

/**
 * The picked photos, videos or files before they are sent (`REQUIREMENTS.md` FR-C5): from the gallery and the camera
 * ([AttachmentKind.Media]) or from the file picker ([AttachmentKind.Document]), each being prepared or ready, in the
 * order they were picked. They go out once all are ready; [canSchedule] whether the send menu offers scheduling.
 */
@Immutable
data class AttachPreview(
    val kind: AttachmentKind,
    val items: ImmutableList<PreviewItem>,
    val canSchedule: Boolean = true,
) {
    /** Every item is ready to send. */
    val isReady: Boolean get() = items.isNotEmpty() && items.none { it is PreviewItem.Preparing }

    /** The attachments ready to send, in their order. */
    val attachments: List<Attachment> get() = items.mapNotNull { (it as? PreviewItem.Ready)?.attachment }
}

/** One picked file in the [AttachPreview], by a [key] that stays while the preview lives. */
@Immutable
sealed interface PreviewItem {
    val key: Long

    /** Being copied and prepared. */
    data class Preparing(
        override val key: Long,
    ) : PreviewItem

    /** Ready to send as [attachment]. */
    data class Ready(
        override val key: Long,
        val attachment: Attachment,
    ) : PreviewItem
}

/**
 * The form of a new poll, while it is open (`REQUIREMENTS.md` FR-C5); what it holds is the view model's `PollEditor`.
 * In a channel ([isChannel]) polls are always anonymous; [canSchedule] whether the send menu offers scheduling.
 */
@Immutable
data class PollSheet(
    val isChannel: Boolean = false,
    val canSchedule: Boolean = true,
)

/**
 * The upload of a message being sent from this device: [progress] from 0 to 1, or null while TDLib has not started it
 * or not told its size.
 */
@Immutable
data class MessageUpload(
    val progress: Float?,
)
