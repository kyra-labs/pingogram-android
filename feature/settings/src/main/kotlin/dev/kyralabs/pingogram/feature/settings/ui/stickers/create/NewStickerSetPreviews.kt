package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** Sample states of making a sticker set, for previews and goldens. */
internal object NewStickerSetPreviewData {
    val name =
        NewStickerSetUiState(title = "Office cats", name = "officecats_kl", nameCheck = NameCheck.Available)

    val taken = name.copy(name = "officecats", nameCheck = NameCheck.Occupied)

    val stickers =
        name.copy(
            step = NewStickerSetStep.Stickers,
            stickers =
                persistentListOf(
                    StickerTile("/none/1.webp", "😺"),
                    StickerTile("/none/2.webp", "😴"),
                    StickerTile("/none/3.webp", "😂"),
                    StickerTile("/none/4.webp", "❤"),
                    StickerTile("/none/5.webp", "👍"),
                ),
        )

    val published = stickers.copy(step = NewStickerSetStep.Published, published = 42)

    val emoji =
        persistentListOf(
            EmojiGroup(
                EmojiCategory.SmileysAndEmotion,
                persistentListOf("😀", "😂", "🙂", "😺", "😴", "❤", "👍", "🎉"),
            ),
            EmojiGroup(EmojiCategory.AnimalsAndNature, persistentListOf("🐱", "🐶", "🐝", "🌻")),
        )
}

internal class NewStickerSetStates : PreviewParameterProvider<NewStickerSetUiState> {
    override val values =
        sequenceOf(
            NewStickerSetPreviewData.name,
            NewStickerSetPreviewData.taken,
            NewStickerSetPreviewData.stickers,
            NewStickerSetPreviewData.published,
        )
}

@PingogramPreviews
@Composable
private fun NewStickerSetScreenPreview(
    @PreviewParameter(NewStickerSetStates::class) state: NewStickerSetUiState,
) {
    PingogramPreview {
        NewStickerSetScreen(
            state = state,
            emoji = persistentListOf(),
            onEvent = {},
            onShare = {},
            onOpenSet = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun EmojiChoicesPreview() {
    PingogramPreview { EmojiChoices(index = 2, groups = NewStickerSetPreviewData.emoji, onEvent = {}) }
}
