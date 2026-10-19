package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.SponsoredCard
import dev.kyralabs.pingogram.feature.chat.ui.SponsoredReportStep
import dev.kyralabs.pingogram.feature.chat.ui.SponsoredSheet
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Sample sponsored messages for previews and screenshot tests: a text with formatting and a link, a recommended
 * channel, a photo with a caption and the sponsor's photo, and a long text; a channel with one below its newest post;
 * and "About this ad" with its information and at its report step. Made up: no real advertiser.
 */
internal object SponsoredPreviewData {
    /** Sponsored messages have large ids, apart from those of the messages. */
    private const val AD_ID = 1L shl 30

    /** A sponsor's photo with a single size, which the preview images fill with a color. */
    private val sponsorPhoto =
        Photo(sizes = persistentListOf(PhotoSize("s", 160, 160, FileRef(90, 0))), minithumbnail = null)

    private const val TEXT = "Plan trips with friends, share the costs and keep every booking in one place."

    val text =
        SponsoredCard(
            id = AD_ID,
            isRecommended = false,
            title = "Trip Planner",
            text =
                FormattedText(
                    "$TEXT Try it free at example.org",
                    persistentListOf(
                        TextEntity(offset = 0, length = 10, type = TextEntityType.Bold),
                        TextEntity(offset = TEXT.length + 1 + 15, length = 11, type = TextEntityType.Url),
                    ),
                ),
            media = null,
            sponsorPhoto = null,
            buttonText = "Open app",
        )

    val recommended =
        text.copy(
            id = AD_ID + 1,
            isRecommended = true,
            title = "Compose Weekly",
            text = FormattedText("News, talks and samples about Jetpack Compose, every week.", persistentListOf()),
            buttonText = "View channel",
        )

    val photo =
        text.copy(
            id = AD_ID + 2,
            title = "Night Sky Tours",
            text = FormattedText("Watch the stars from the darkest valleys in the country.", persistentListOf()),
            media = ContentPreviewData.photo(minithumbnail = null, firstFileId = 80),
            sponsorPhoto = sponsorPhoto,
            buttonText = "Book a tour",
        )

    val longText =
        text.copy(
            id = AD_ID + 3,
            title = "",
            text = FormattedText(List(4) { TEXT }.joinToString(" "), persistentListOf()),
            buttonText = "Learn more",
        )

    /** A channel whose newest post is followed by a sponsored message, as the conversation shows it. */
    val channel: ChatUiState =
        ChatPreviewData.channel.let { channel ->
            val content = channel.content as ChatContent.Content
            val items = listOf(ChatItem.Sponsored(text, UiText.resource(R.string.chat_date_today))) + content.items
            channel.copy(content = content.copy(items = items.toImmutableList()))
        }

    val info =
        SponsoredSheet(
            messageId = AD_ID,
            isRecommended = false,
            sponsorInfo = "Trip Planner Ltd, 1 Example Street, Springfield",
            additionalInfo = "Shown to subscribers of travel channels.",
            canBeReported = true,
        )

    val reportStep =
        info.copy(
            report =
                SponsoredReportStep(
                    title = "Why are you reporting this ad?",
                    options =
                        persistentListOf(
                            "I don't like it",
                            "Misleading or a scam",
                            "Illegal content",
                            "Sexual content",
                            "Other",
                        ),
                ),
        )
}
