package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.attach.ChatAttachments
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatForwarding
import dev.kyralabs.pingogram.feature.chat.ui.poll.ChatPolls

/**
 * Hands the events of the message actions, the reactions, the forward sheet, the next channel and the playback speed
 * to the parts that handle them, and the others to [attachEvents]: the events [ChatViewModel.onEvent] does not route
 * itself.
 */
internal class ChatMessageEvents(
    private val messageActions: ChatMessageActions,
    private val nextChannels: ChatNextChannel,
    private val playback: ChatPlayback,
    private val forwarding: ChatForwarding,
    private val attachEvents: ChatAttachEvents,
) {
    /** Hands [event] to the part that handles it. */
    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.ShowMessageActions -> messageActions.show(event.messageId, link = event.link)
            is ChatEvent.ShowAllReactions -> messageActions.show(event.messageId, showsPicker = true)
            is ChatEvent.ShowInteractions -> messageActions.showInteractions(event.messageId, event.reactions)
            ChatEvent.ExpandReactions -> messageActions.showPicker(true)
            ChatEvent.CollapseReactions -> messageActions.showPicker(false)
            ChatEvent.DismissMessageActions -> messageActions.dismiss()
            is ChatEvent.Reply -> messageActions.reply(event.messageId)
            is ChatEvent.Copy -> messageActions.copy(event.messageId)
            is ChatEvent.Edit -> messageActions.edit(event.messageId)
            is ChatEvent.Pin -> messageActions.pin(event.messageId, event.onlyForSelf)
            is ChatEvent.Unpin -> messageActions.unpin(event.messageId)
            is ChatEvent.Delete -> messageActions.delete(event.messageId)
            is ChatEvent.ConfirmDelete -> messageActions.confirmDelete(event.forEveryone)
            ChatEvent.DismissDelete -> messageActions.dismissDelete()
            is ChatEvent.Resend -> messageActions.resend(event.messageId)
            is ChatEvent.ToggleReaction -> messageActions.toggleReaction(event.messageId, event.label)
            is ChatEvent.Forward -> messageActions.forward(event.messageId)
            is ChatEvent.OpenNextChannel -> nextChannels.open(event.next)
            ChatEvent.ChangePlaybackSpeed -> playback.changeSpeed()
            is ForwardEvent -> forwarding.onEvent(event)
            else -> attachEvents.onEvent(event)
        }
    }
}

/**
 * Hands the events of the attach menu, its previews and the poll and checklist forms to [attachments]. A poll goes out
 * anonymous in a channel, which [isChannelPoll] tells.
 */
internal class ChatAttachEvents(
    private val attachments: ChatAttachments,
    private val composer: ChatComposer,
    private val isChannelPoll: () -> Boolean,
) {
    /** Hands [event] to the attachments; the other events are not theirs. */
    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.AttachMedia -> attachments.pickMedia(event.uris)

            is ChatEvent.AttachFiles -> attachments.pickFiles(event.uris)

            ChatEvent.TakePhoto -> attachments.takePhoto()

            is ChatEvent.PhotoTaken -> attachments.photoTaken(event.taken)

            is ChatEvent.AttachContact -> attachments.sendContact(event.uri)

            is ChatEvent.SendLocation -> attachments.sendLocation(event.location, event.options)

            is ChatEvent.SendSticker -> attachments.sendSticker(event.sticker)

            is ChatEvent.Wave -> attachments.wave(event.messageId)

            is ChatEvent.SendAnimation -> attachments.sendAnimation(event, composer)

            is ChatEvent.RemoveAttachment -> attachments.remove(event.key)

            is ChatEvent.SetOriginalQuality -> attachments.setOriginal(event.original)

            ChatEvent.DismissAttachments -> attachments.dismissPreview()

            is ChatEvent.SendAttachments -> attachments.sendPreview(event.options)

            ChatEvent.OpenPoll -> attachments.openPoll()

            ChatEvent.DismissPoll -> attachments.dismissPoll()

            is ChatEvent.PickPollPhoto -> attachments.pickPollPhoto(event.uri)

            ChatEvent.RemovePollPhoto -> attachments.removePollPhoto()

            // A poll goes out anonymous in a channel.
            is ChatEvent.SendPoll -> attachments.sendPoll(event.options, isChannelPoll())

            ChatEvent.OpenChecklist -> attachments.openChecklist()

            ChatEvent.DismissChecklist -> attachments.dismissChecklist()

            is ChatEvent.SendChecklist -> attachments.sendChecklist(event.options)

            is ChatEvent.CancelUpload -> attachments.cancelUpload(event.messageIds)

            // The other events are handled by ChatViewModel.onEvent and ChatMessageEvents.
            else -> Unit
        }
    }
}

/**
 * Hands this event of a poll of chat [chatId] on: taking a vote back and stopping a poll go through the message
 * actions' checks, the statistics open through [onEffect], the rest goes to [polls].
 */
internal fun PollEvent.dispatch(
    chatId: Long,
    messageActions: ChatMessageActions,
    polls: ChatPolls,
    onEffect: (ChatEffect) -> Unit,
) {
    when (this) {
        is PollEvent.RetractVote -> messageActions.retractVote(messageId)
        is PollEvent.StopPoll -> messageActions.stopPoll(messageId)
        is PollEvent.OpenStatistics -> onEffect(ChatEffect.OpenPollStatistics(chatId, messageId))
        else -> polls.onEvent(this)
    }
}
