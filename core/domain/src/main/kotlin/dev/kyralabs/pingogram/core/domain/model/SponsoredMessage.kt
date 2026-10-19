package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The sponsored messages TDLib returns for a chat (`REQUIREMENTS.md` §2): channels and chats with bots. They are shown
 * where TDLib returns them, clearly labelled, and never hidden or changed. [messagesBetween] is the fewest ordinary
 * messages between two shown ones; 0 means only one is shown, after all the chat's messages.
 */
data class SponsoredMessages(
    val messages: ImmutableList<SponsoredMessage>,
    val messagesBetween: Int,
) {
    companion object {
        val None = SponsoredMessages(persistentListOf(), messagesBetween = 0)
    }
}

/**
 * A sponsored message: an ad, or a recommended channel when [isRecommended]. It is not a message of the history: [id]
 * is unique in the chat among ordinary and sponsored messages, and viewing it goes to TDLib like a message's.
 *
 * `toString()` leaves the texts and the link out, like the models of messages (`REQUIREMENTS.md` §6.2).
 *
 * @property title the sponsor's name, shown above the text; may be empty.
 * @property content a text, or a photo, video or GIF with the text as its caption; [MessageContent.Unsupported] for a
 *   kind a newer TDLib adds, which still shows the label, the title and the button.
 * @property buttonText what the advertiser's button says; opening it opens [AdvertisementSponsor.url].
 * @property accentColorId TDLib's accent color of the sponsor; not shown, colors come from the theme's roles
 *   (`docs/DESIGN_SYSTEM.md` §2.2).
 * @property additionalInfo more about the ad, for "About this ad"; empty if there is none.
 */
data class SponsoredMessage(
    val id: Long,
    val isRecommended: Boolean,
    val canBeReported: Boolean,
    val title: String,
    val content: MessageContent,
    val sponsor: AdvertisementSponsor,
    val buttonText: String,
    val accentColorId: Int,
    val additionalInfo: String,
) {
    override fun toString() = "SponsoredMessage(id=$id, isRecommended=$isRecommended, canBeReported=$canBeReported, …)"
}

/**
 * Who placed an ad: [url] opens when the user opens it; [photo] is null where it must not be shown; [info] says who
 * the advertiser is (legal information), empty if TDLib has none.
 */
data class AdvertisementSponsor(
    val url: String,
    val photo: Photo?,
    val info: String,
) {
    override fun toString() = "AdvertisementSponsor(…)"
}

/** What came of reporting a sponsored message (`SponsoredMessageRepository.report`). */
sealed interface SponsoredReportResult {
    /** Telegram has the report. */
    data object Reported : SponsoredReportResult

    /** Telegram hid sponsored messages for the user, in every chat. */
    data object AdsHidden : SponsoredReportResult

    /** Hiding ads needs Telegram Premium, which Pingogram never offers (`REQUIREMENTS.md` §1.2). */
    data object PremiumRequired : SponsoredReportResult

    /** Telegram did not take the report. */
    data object Failed : SponsoredReportResult

    /** Report again with one of [options]: [title] asks why. */
    data class OptionRequired(
        val title: String,
        val options: ImmutableList<ReportOption>,
    ) : SponsoredReportResult
}

/** A reason to choose when reporting a sponsored message: [text] says it, [id] goes back to TDLib. */
data class ReportOption(
    val id: ReportOptionId,
    val text: String,
)

/**
 * TDLib's opaque id of a [ReportOption]. It compares by its bytes, which nothing writes to after mapping;
 * [Initial] is the first report's, before any option was asked for.
 */
class ReportOptionId(
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ReportOptionId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString() = "ReportOptionId(${bytes.size} bytes)"

    companion object {
        val Initial = ReportOptionId(ByteArray(0))
    }
}
