package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import kotlinx.collections.immutable.persistentListOf

/** The page's states for previews and goldens: the pins, loading, none, none found and an error. */
internal class PinnedPagePreviewStates : PreviewParameterProvider<PinnedPageUiState> {
    override val values =
        sequenceOf(
            PinnedPagePreviewData.content,
            PinnedPagePreviewData.content.copy(content = PinnedPageContent.Loading),
            PinnedPagePreviewData.content.copy(
                total = 0,
                filters = persistentListOf(),
                content = PinnedPageContent.Empty(filtered = false),
            ),
            PinnedPagePreviewData.content.copy(
                selected = PinnedFilter.Links,
                content = PinnedPageContent.Empty(filtered = true),
            ),
            PinnedPagePreviewData.content.copy(content = PinnedPageContent.Error(DomainError.Network)),
        )
}

/** Made-up pins of a beta group, as the owner's design shows them. */
internal object PinnedPagePreviewData {
    @Suppress("LongParameterList") // A made-up card's fields, named at each call.
    private fun card(
        id: Long,
        number: Int,
        name: String,
        hue: Int,
        time: String,
        text: String,
        file: PinnedFile? = null,
        link: PinnedLink? = null,
        clip: PinnedClip? = null,
        bold: Int = 0,
    ) = PinnedCard(
        messageId = id,
        number = number,
        name = UiText.Plain(name),
        avatar = SenderAvatar(id, UiText.Plain(name), null, null),
        avatarShape = AvatarShape.Circle,
        senderHue = hue,
        dateTime = UiText.Plain(time),
        text =
            FormattedText(
                text,
                if (bold >
                    0
                ) {
                    persistentListOf(TextEntity(0, bold, TextEntityType.Bold))
                } else {
                    persistentListOf()
                },
            ),
        file = file,
        link = link,
        clip = clip,
    )

    val cards =
        persistentListOf(
            card(
                23,
                23,
                "Riya Sharma",
                1,
                "Nov 14, 9:41 PM",
                "Release notes for 2.2.0: the floating folder bar, sticker search and Start a call. " +
                    "Please test on your phones.",
                bold = 13,
            ),
            card(
                22,
                22,
                "Owen Park",
                4,
                "Nov 12, 6:05 PM",
                "Debug build for testers",
                file = PinnedFile("pingogram-2.2.0-arm64-v8a.apk", 52_000_000, "APK"),
            ),
            card(
                20,
                20,
                "Riya Sharma",
                1,
                "Nov 8, 4:12 PM",
                "How the floating folder bar moves",
                clip = PinnedClip(fileId = 0, width = 1280, height = 720, duration = 42),
            ),
            card(
                21,
                21,
                "Sahil Rao",
                6,
                "Nov 3, 11:20 AM",
                "Read this before reporting a bug",
                link =
                    PinnedLink(
                        "github.com",
                        "Testing guide",
                        "How to send feedback from the app, with the crash report and the log.",
                    ),
            ),
        )

    val content =
        PinnedPageUiState(
            chatTitle = "Pingogram Beta",
            total = 23,
            filters =
                persistentListOf(
                    PinnedFilterChip(PinnedFilter.All, 23),
                    PinnedFilterChip(PinnedFilter.Text, 14),
                    PinnedFilterChip(PinnedFilter.Media, 5),
                    PinnedFilterChip(PinnedFilter.Files, 2),
                    PinnedFilterChip(PinnedFilter.Links, 2),
                ),
            content =
                PinnedPageContent.Content(
                    persistentListOf(
                        PinnedMonth("November", cards),
                    ),
                ),
            canUnpin = true,
        )
}
