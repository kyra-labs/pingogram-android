package dev.kyralabs.pingogram.feature.profile.chatinfo.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** Sample welcome message pages for previews and goldens. */
internal object WelcomeMessagesPreviewData {
    private const val WELCOME =
        "Welcome to Pixel Photographers! Three rules: credit the photographer, keep critique kind, and post " +
            "weekly-theme shots with #theme."

    val photo =
        Photo(persistentListOf(PhotoSize("x", 800, 600, FileRef(id = 0, size = 0))), minithumbnail = null)

    val on =
        WelcomeMessagesUiState.Content(
            messages =
                persistentListOf(
                    WelcomeItem(
                        id = 1,
                        text =
                            FormattedText(
                                WELCOME,
                                persistentListOf(TextEntity(11, 20, TextEntityType.Bold)),
                            ),
                        photo = null,
                    ),
                    WelcomeItem(
                        id = 2,
                        text =
                            FormattedText(
                                "This week's theme: Rain. Best shot gets pinned on Sunday.",
                                persistentListOf(),
                            ),
                        photo = photo,
                    ),
                ),
            countMax = 5,
        )

    val off = on.copy(messages = persistentListOf())

    val newEditor = WelcomeEditor(messageId = null, text = "", photo = null)

    val photoEditor =
        WelcomeEditor(
            messageId = 2,
            text = "This week's theme: Rain. Best shot gets pinned on Sunday.",
            photo = EditorPhoto.Kept(photo),
        )

    val formattedEditor = WelcomeEditor(messageId = 1, text = WELCOME, photo = null, isFormatted = true)
}

internal class WelcomeMessagesStates : PreviewParameterProvider<WelcomeMessagesUiState> {
    override val values =
        sequenceOf(
            WelcomeMessagesPreviewData.on,
            WelcomeMessagesPreviewData.off,
            WelcomeMessagesUiState.Loading,
            WelcomeMessagesUiState.Error(DomainError.Network),
        )
}

@PingogramPreviews
@Composable
private fun WelcomeMessagesScreenPreview(
    @PreviewParameter(WelcomeMessagesStates::class) state: WelcomeMessagesUiState,
) {
    PingogramPreview { WelcomeMessagesScreen(state, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun WelcomeEditorPreview() {
    PingogramPreview { WelcomeEditorContent(WelcomeMessagesPreviewData.photoEditor, onEvent = {}) }
}
