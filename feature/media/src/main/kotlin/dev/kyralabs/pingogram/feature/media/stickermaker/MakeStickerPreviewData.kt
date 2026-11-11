package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.graphics.painter.Painter
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import kotlinx.collections.immutable.persistentListOf

/** The sticker maker's states for previews and goldens (boards 14a, 14b). */
internal object MakeStickerPreviewData {
    val editing =
        MakeStickerUiState(
            photo = StickerPhoto.Ready("/preview/photo.jpg"),
            crop = StickerCrop(zoom = 1.2f, panX = 0.05f),
            layers =
                persistentListOf(
                    StickerLayer(0, LayerKind.Text, "Monday mood", x = 0.5f, y = 0.82f),
                    StickerLayer(1, LayerKind.Emoji, "😴", x = 0.78f, y = 0.2f),
                ),
            selected = 1,
        )

    val loading = MakeStickerUiState(photo = StickerPhoto.Loading(0.4f))

    val failed = MakeStickerUiState(photo = StickerPhoto.Failed)

    private val sets =
        OwnedSets.Loaded(
            persistentListOf(
                OwnedSet("Office cats", "officecats_kl", 12, cover = null),
                OwnedSet("Daily moods", "dailymoods", 30, cover = null),
            ),
        )

    val choose = AddToSetState("/preview/sticker.webp", emoji = "😴", sets = sets, choice = "officecats_kl")

    val chooseLoading = AddToSetState("/preview/sticker.webp")

    val chooseFailed = AddToSetState("/preview/sticker.webp", sets = OwnedSets.Failed(DomainError.Network))

    val newSet =
        AddToSetState(
            "/preview/sticker.webp",
            sets = sets,
            choice = AddToSetState.NEW_SET,
            step = AddToSetStep.NewSet,
            newSet = NewSetDraft("Office cats", "office_cats_kl", check = StickerSetNameCheck.Available),
        )

    val taken =
        newSet.copy(newSet = NewSetDraft("Cats", "cats", nameTyped = true, check = StickerSetNameCheck.Occupied))

    val refused = choose.copy(error = DomainError.InputRejected)

    val done =
        choose.copy(step = AddToSetStep.Done, added = OwnedSet("Office cats", "officecats_kl", 13, cover = null))
}

/** A photo for previews and goldens: a gradient of the theme's colors, as a painter without a size of its own. */
@Composable
internal fun previewStickerPhoto(): Painter {
    val from = MaterialTheme.colorScheme.tertiaryContainer
    val to = MaterialTheme.colorScheme.primary
    return remember(from, to) { BrushPainter(Brush.linearGradient(listOf(from, to))) }
}
