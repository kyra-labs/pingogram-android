package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AdvertisementSponsor
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessage
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

// Sponsored messages of channels and bots (REQUIREMENTS.md §2). Every message TDLib returns is kept, whatever its
// content: an ad is never stripped.

internal fun TdApi.SponsoredMessages.toSponsoredMessages(): SponsoredMessages =
    SponsoredMessages(
        messages = messages.map { it.toSponsoredMessage() }.toImmutableList(),
        messagesBetween = messagesBetween.coerceAtLeast(0),
    )

/**
 * The sponsored message, with its content as a message's. td_api.tl allows a text, a photo, a video or a GIF; any
 * other content (a newer TDLib) is [MessageContent.Unsupported], and the message still shows its label, title and
 * button.
 */
internal fun TdApi.SponsoredMessage.toSponsoredMessage(): SponsoredMessage =
    SponsoredMessage(
        id = messageId,
        isRecommended = isRecommended,
        canBeReported = canBeReported,
        title = title.orEmpty(),
        content = content?.toSponsoredContent() ?: MessageContent.Unsupported,
        sponsor = sponsor.toAdvertisementSponsor(),
        buttonText = buttonText.orEmpty(),
        accentColorId = accentColorId,
        additionalInfo = additionalInfo.orEmpty(),
    )

internal fun TdApi.ReportSponsoredResult.toSponsoredReportResult(): SponsoredReportResult =
    when (this) {
        is TdApi.ReportSponsoredResultOk -> {
            SponsoredReportResult.Reported
        }

        is TdApi.ReportSponsoredResultAdsHidden -> {
            SponsoredReportResult.AdsHidden
        }

        is TdApi.ReportSponsoredResultPremiumRequired -> {
            SponsoredReportResult.PremiumRequired
        }

        is TdApi.ReportSponsoredResultOptionRequired -> {
            SponsoredReportResult.OptionRequired(
                title = title.orEmpty(),
                options = options.map { ReportOption(ReportOptionId(it.id), it.text.orEmpty()) }.toImmutableList(),
            )
        }

        // ReportSponsoredResultFailed, or a result a newer TDLib adds.
        else -> {
            SponsoredReportResult.Failed
        }
    }

/** A sponsored message has no sender and is no service message. */
private fun TdApi.MessageContent.toSponsoredContent(): MessageContent =
    when (val content = toMessageContent(sender = null, isChannelPost = true)) {
        is MessageContent.Text, is MessageContent.Photo, is MessageContent.Video, is MessageContent.Animation -> content
        else -> MessageContent.Unsupported
    }

private fun TdApi.AdvertisementSponsor?.toAdvertisementSponsor(): AdvertisementSponsor =
    AdvertisementSponsor(
        url = this?.url.orEmpty(),
        photo = this?.photo?.toPhoto(),
        info = this?.info.orEmpty(),
    )
