package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/**
 * The event of a service message from [sender], in a channel if [isChannelPost]; null if this is not a service message.
 * The sender tells joining from being added and leaving from being removed.
 */
internal fun TdApi.MessageContent.toServiceAction(
    sender: MessageSender?,
    isChannelPost: Boolean,
): ServiceAction? =
    when (this) {
        is TdApi.MessageBasicGroupChatCreate -> {
            ServiceAction.BasicGroupCreated(title, memberUserIds.asList().toImmutableList())
        }

        is TdApi.MessageSupergroupChatCreate -> {
            if (isChannelPost) ServiceAction.ChannelCreated(title) else ServiceAction.SupergroupCreated(title)
        }

        is TdApi.MessageChatUpgradeTo -> {
            ServiceAction.UpgradedToSupergroup(supergroupId)
        }

        is TdApi.MessageChatUpgradeFrom -> {
            ServiceAction.UpgradedFromBasicGroup(title, basicGroupId)
        }

        is TdApi.MessageChatChangeTitle -> {
            ServiceAction.TitleChanged(title)
        }

        is TdApi.MessageChatChangePhoto -> {
            ServiceAction.PhotoChanged(photo.toPhoto())
        }

        is TdApi.MessageChatDeletePhoto -> {
            ServiceAction.PhotoDeleted
        }

        // A user who joins a public group adds themselves.
        is TdApi.MessageChatAddMembers -> {
            if (memberUserIds.singleOrNull() == sender.userId) {
                ServiceAction.MemberJoined
            } else {
                ServiceAction.MembersAdded(memberUserIds.asList().toImmutableList())
            }
        }

        is TdApi.MessageChatJoinByLink -> {
            ServiceAction.MemberJoinedByLink
        }

        is TdApi.MessageChatJoinByRequest -> {
            ServiceAction.MemberJoinedByRequest
        }

        // A user who leaves removes themselves.
        is TdApi.MessageChatDeleteMember -> {
            if (userId == sender.userId) ServiceAction.MemberLeft else ServiceAction.MemberRemoved(userId)
        }

        else -> {
            toActivityAction()
        }
    }

/** Pins, screenshots, calls, video chats, settings and custom events; [toServiceAction] covers the chat and members. */
private fun TdApi.MessageContent.toActivityAction(): ServiceAction? =
    when (this) {
        is TdApi.MessagePinMessage -> {
            ServiceAction.MessagePinned(messageId)
        }

        is TdApi.MessageScreenshotTaken -> {
            ServiceAction.ScreenshotTaken
        }

        is TdApi.MessageCall -> {
            ServiceAction.Call(isVideo, duration, discardReason.toCallDiscardReason())
        }

        is TdApi.MessageGroupCall -> {
            ServiceAction.GroupCall(isActive, wasMissed, isVideo, duration)
        }

        is TdApi.MessageVideoChatScheduled -> {
            ServiceAction.VideoChatScheduled(startDate.toLong())
        }

        is TdApi.MessageVideoChatStarted -> {
            ServiceAction.VideoChatStarted
        }

        is TdApi.MessageVideoChatEnded -> {
            ServiceAction.VideoChatEnded(duration)
        }

        is TdApi.MessageInviteVideoChatParticipants -> {
            ServiceAction.VideoChatInvited(userIds.asList().toImmutableList())
        }

        is TdApi.MessageChatSetMessageAutoDeleteTime -> {
            ServiceAction.AutoDeleteTimerChanged(messageAutoDeleteTime, fromUserId)
        }

        is TdApi.MessageContactRegistered -> {
            ServiceAction.ContactRegistered
        }

        is TdApi.MessageCustomServiceAction -> {
            ServiceAction.Custom(text)
        }

        else -> {
            if (javaClass in OTHER_SERVICE_CONTENTS) ServiceAction.Other else null
        }
    }

private val MessageSender?.userId: Long?
    get() = (this as? MessageSender.User)?.userId

internal fun TdApi.CallDiscardReason?.toCallDiscardReason(): CallDiscardReason =
    when (this) {
        is TdApi.CallDiscardReasonMissed -> CallDiscardReason.Missed

        is TdApi.CallDiscardReasonDeclined -> CallDiscardReason.Declined

        is TdApi.CallDiscardReasonDisconnected -> CallDiscardReason.Disconnected

        is TdApi.CallDiscardReasonHungUp -> CallDiscardReason.HungUp

        is TdApi.CallDiscardReasonUpgradeToGroupCall -> CallDiscardReason.UpgradedToGroupCall

        // Empty, and a reason added by a newer TDLib.
        else -> CallDiscardReason.Unknown
    }

/**
 * Service messages of the pinned TDLib that have no [ServiceAction] of their own. Contents outside this set and
 * [toServiceAction] are regular messages, or added by a newer TDLib: they become `MessageContent.Unsupported`.
 */
internal val OTHER_SERVICE_CONTENTS: Set<Class<out TdApi.MessageContent>> =
    setOf(
        TdApi.MessageBotWriteAccessAllowed::class.java,
        TdApi.MessageChatAddedToCommunity::class.java,
        TdApi.MessageChatBoost::class.java,
        TdApi.MessageChatHasProtectedContentDisableRequested::class.java,
        TdApi.MessageChatHasProtectedContentToggled::class.java,
        TdApi.MessageChatJoinFromCommunity::class.java,
        TdApi.MessageChatOwnerChanged::class.java,
        TdApi.MessageChatOwnerLeft::class.java,
        TdApi.MessageChatRemovedFromCommunity::class.java,
        TdApi.MessageChatSetBackground::class.java,
        TdApi.MessageChatSetTheme::class.java,
        TdApi.MessageChatShared::class.java,
        TdApi.MessageChecklistTasksAdded::class.java,
        TdApi.MessageChecklistTasksDone::class.java,
        TdApi.MessageDirectMessagePriceChanged::class.java,
        TdApi.MessageForumTopicCreated::class.java,
        TdApi.MessageForumTopicEdited::class.java,
        TdApi.MessageForumTopicIsClosedToggled::class.java,
        TdApi.MessageForumTopicIsHiddenToggled::class.java,
        TdApi.MessageGameScore::class.java,
        TdApi.MessageGift::class.java,
        TdApi.MessageGiftedGrams::class.java,
        TdApi.MessageGiftedPremium::class.java,
        TdApi.MessageGiftedStars::class.java,
        TdApi.MessageGiveawayCompleted::class.java,
        TdApi.MessageGiveawayCreated::class.java,
        TdApi.MessageGiveawayPrizeStars::class.java,
        TdApi.MessageManagedBotCreated::class.java,
        TdApi.MessagePaidMessagePriceChanged::class.java,
        TdApi.MessagePaidMessagesRefunded::class.java,
        TdApi.MessagePassportDataReceived::class.java,
        TdApi.MessagePassportDataSent::class.java,
        TdApi.MessagePaymentRefunded::class.java,
        TdApi.MessagePaymentSuccessful::class.java,
        TdApi.MessagePaymentSuccessfulBot::class.java,
        TdApi.MessagePollOptionAdded::class.java,
        TdApi.MessagePollOptionDeleted::class.java,
        TdApi.MessagePremiumGiftCode::class.java,
        TdApi.MessageProximityAlertTriggered::class.java,
        TdApi.MessageRefundedUpgradedGift::class.java,
        TdApi.MessageSuggestBirthdate::class.java,
        TdApi.MessageSuggestProfilePhoto::class.java,
        TdApi.MessageSuggestedPostApprovalFailed::class.java,
        TdApi.MessageSuggestedPostApproved::class.java,
        TdApi.MessageSuggestedPostDeclined::class.java,
        TdApi.MessageSuggestedPostPaid::class.java,
        TdApi.MessageSuggestedPostRefunded::class.java,
        TdApi.MessageTonConnectRequest::class.java,
        TdApi.MessageTonWalletTransfer::class.java,
        TdApi.MessageUpgradedGift::class.java,
        TdApi.MessageUpgradedGiftPurchaseOffer::class.java,
        TdApi.MessageUpgradedGiftPurchaseOfferRejected::class.java,
        TdApi.MessageUsersShared::class.java,
        TdApi.MessageWebAppDataReceived::class.java,
        TdApi.MessageWebAppDataSent::class.java,
    )
