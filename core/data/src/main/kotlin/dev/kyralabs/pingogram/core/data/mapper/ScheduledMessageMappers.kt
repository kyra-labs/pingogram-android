package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import org.drinkless.tdlib.TdApi

/** This message as a [ScheduledMessage]; null for a message that is not scheduled, or a state of a newer TDLib. */
internal fun TdApi.Message.toScheduledMessage(): ScheduledMessage? =
    schedulingState?.toMessageSchedule()?.let { ScheduledMessage(toMessage(), it) }

/** Null for a scheduling state added by a newer TDLib. */
internal fun TdApi.MessageSchedulingState.toMessageSchedule(): MessageSchedule? =
    when (this) {
        is TdApi.MessageSchedulingStateSendAtDate -> {
            MessageSchedule.At(sendDate.toLong(), repeatPeriod.coerceAtLeast(0))
        }

        is TdApi.MessageSchedulingStateSendWhenOnline -> {
            MessageSchedule.WhenOnline
        }

        is TdApi.MessageSchedulingStateSendWhenVideoProcessed -> {
            MessageSchedule.WhenVideoProcessed(sendDate.toLong())
        }

        else -> {
            null
        }
    }
