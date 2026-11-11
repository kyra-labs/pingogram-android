package dev.kyralabs.pingogram.feature.media.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.MakeStickerRoute
import dev.kyralabs.pingogram.feature.media.stickermaker.MakeStickerRoute as MakeSticker

/**
 * Adds making a sticker from a photo, [MakeStickerRoute] (boards 14a, 14b), to the graph, with the `NavHost`'s
 * transitions for full-screen destinations. [onClose] leaves it, also once the sticker is in its set.
 */
fun NavGraphBuilder.makeStickerScreen(onClose: () -> Unit) {
    composable<MakeStickerRoute> { MakeSticker(onClose = onClose) }
}
