package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import kotlinx.collections.immutable.ImmutableList

/**
 * A sponsored message as its card shows it (`REQUIREMENTS.md` §2; `docs/DESIGN_SYSTEM.md` §6 "Sponsored message"),
 * exactly as TDLib returned it: nothing is left out or changed.
 *
 * @property isRecommended labelled "Recommended" instead of "Sponsored": a recommended channel.
 * @property title the sponsor's name; may be empty.
 * @property text the text, or the media's caption, with its formatting; may be empty.
 * @property media the photo, video or GIF; null for a text.
 * @property sponsorPhoto the sponsor's photo beside the label; null where there is none, or it must not be shown.
 * @property buttonText what the advertiser's button says.
 */
@Immutable
data class SponsoredCard(
    val id: Long,
    val isRecommended: Boolean,
    val title: String,
    val text: FormattedText,
    val media: MessageContent?,
    val sponsorPhoto: Photo?,
    val buttonText: String,
) {
    override fun toString() = "SponsoredCard(id=$id, isRecommended=$isRecommended, …)"
}

/**
 * "About this ad" for sponsored message [messageId] while it is open: who placed it ([sponsorInfo]) and more about it
 * ([additionalInfo]), each empty if TDLib has nothing, and the report entry if [canBeReported]. While a report asks
 * why, [report] is the step that shows its options; [isReporting] while a report is on its way.
 */
@Immutable
data class SponsoredSheet(
    val messageId: Long,
    val isRecommended: Boolean,
    val sponsorInfo: String,
    val additionalInfo: String,
    val canBeReported: Boolean,
    val report: SponsoredReportStep? = null,
    val isReporting: Boolean = false,
) {
    override fun toString() = "SponsoredSheet(messageId=$messageId, report=${report != null}, …)"
}

/** A report that asks why: [title] asks, and the user picks one of [options]. */
@Immutable
data class SponsoredReportStep(
    val title: String,
    val options: ImmutableList<String>,
)

/** What the user does to a sponsored message; [ChatViewModel] hands these to [ChatSponsoredMessages]. */
sealed interface SponsoredEvent : ChatEvent {
    /** The whole card of [messageId] is on screen: it goes to TDLib as viewed, once. */
    data class Viewed(
        val messageId: Long,
    ) : SponsoredEvent

    /**
     * The user opened [messageId]: its button ([url] null, the sponsor's link opens), a link in its text ([url]), or
     * its media ([isMediaClick]).
     */
    data class Open(
        val messageId: Long,
        val isMediaClick: Boolean = false,
        val url: String? = null,
    ) : SponsoredEvent {
        override fun toString() = "Open(messageId=$messageId, isMediaClick=$isMediaClick, …)"
    }

    /** Open "About this ad" of [messageId]. */
    data class ShowInfo(
        val messageId: Long,
    ) : SponsoredEvent

    /** Close "About this ad". */
    data object DismissInfo : SponsoredEvent

    /** Report the ad "About this ad" shows. */
    data object Report : SponsoredEvent

    /** Report it again with the option at [index] of the report step. */
    data class ChooseReportOption(
        val index: Int,
    ) : SponsoredEvent

    /** Back from the report step to the ad's information. */
    data object BackToInfo : SponsoredEvent
}

/** The list key of sponsored message [id]: a string, never equal to a message's id. */
internal fun sponsoredKey(id: Long): String = "sponsored:$id"
