package dev.kyralabs.pingogram.feature.chat.ui.secret

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.EncryptionKeyHash
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.SecretChatPreviewData

/**
 * The image of a secret chat's key (`REQUIREMENTS.md` FR-E1) as td_api.tl describes it: the 36 bytes of [keyHash] as
 * a 12 × 12 square of four colors, two bits a pixel, left to right and top to bottom ([EncryptionKeyHash.pixel]),
 * drawn in the draw phase with `small` corners and an `outline` border, inside the Expressive frame of the QR login
 * (`MaterialShapes.Cookie12Sided` in `primaryContainer`, `docs/DESIGN_SYSTEM.md` §4).
 *
 * The four colors are tonal steps of the theme ([KeyColors]), never fixed hex; the official apps use white, a light,
 * a dark and a middle blue, and the image keeps that order of light and dark, so both people see the same pattern of
 * light and dark whichever app or theme they use. Screen readers hear "Image of the encryption key"; the
 * hexadecimal text beside it ([KeyFingerprint]) is what they compare.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EncryptionKeyImage(
    keyHash: EncryptionKeyHash,
    modifier: Modifier = Modifier,
) {
    val colors = keyColors()
    val description = stringResource(R.string.secret_key_image)
    Box(
        modifier =
            modifier
                .widthIn(max = MaterialTheme.sizes.illustrationMaxSize)
                .fillMaxWidth(KEY_FRAME_FRACTION)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie12Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize(KEY_IMAGE_FRACTION)
                    // The edge pixels may have the frame's tone: an outline keeps the square's edges visible.
                    .border(
                        MaterialTheme.sizes.keyImageOutline,
                        MaterialTheme.colorScheme.outline,
                        MaterialTheme.shapes.small,
                    ).padding(MaterialTheme.sizes.keyImageOutline)
                    .clip(MaterialTheme.shapes.small)
                    .semantics {
                        contentDescription = description
                        role = Role.Image
                    },
        ) {
            val side = EncryptionKeyHash.IMAGE_SIZE
            val cell = Size(size.width / side, size.height / side)
            for (index in 0 until minOf(keyHash.pixelCount, side * side)) {
                drawRect(
                    color = colors[keyHash.pixel(index)],
                    topLeft = Offset((index % side) * cell.width, (index / side) * cell.height),
                    // A hair larger, so no seam shows between cells where they meet at a fraction of a pixel.
                    size = Size(cell.width + 1f, cell.height + 1f),
                )
            }
        }
    }
}

/**
 * The first 32 bytes of [keyHash] in hexadecimal, as td_api.tl allows besides the image: four lines of eight pairs in
 * `bodyMedium` and the system's monospace font (`FontFamily.Monospace`, as code is), left to right in every layout,
 * centred.
 */
@Composable
internal fun KeyFingerprint(
    keyHash: EncryptionKeyHash,
    modifier: Modifier = Modifier,
) {
    val text =
        remember(keyHash) {
            keyHash.hexPairs().chunked(PAIRS_PER_LINE).joinToString("\n") { it.joinToString(" ") }
        }
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        style =
            MaterialTheme.typography.bodyMedium
                .copy(fontFamily = FontFamily.Monospace)
                .numericContent,
    )
}

/**
 * The colors of the key's four kinds of pixels, lightest first as the official palette orders them (white, light,
 * dark, middle): the *Fixed* tones of the theme's primary and tertiary palettes (90, 80, 10 and 30), which keep their
 * tone in dark theme too, so the image is the same in both themes, and different hues tell neighbouring tones apart.
 * Dynamic color moves the hues with the wallpaper.
 */
@Immutable
private class KeyColors(
    private val colors: List<Color>,
) {
    operator fun get(pixel: Int): Color = colors[pixel]
}

@Composable
private fun keyColors(): KeyColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        KeyColors(
            listOf(scheme.primaryFixed, scheme.tertiaryFixedDim, scheme.onPrimaryFixed, scheme.onTertiaryFixedVariant),
        )
    }
}

/** How much of the sheet's width the frame takes, at most `sizes.illustrationMaxSize`; as the QR login's frame. */
private const val KEY_FRAME_FRACTION = 0.8f

/** How much of the frame the image takes: as much as fits inside the cookie's waves, as the QR code does. */
private const val KEY_IMAGE_FRACTION = 0.6f

private const val PAIRS_PER_LINE = 8

@PingogramPreviews
@Composable
private fun EncryptionKeyImagePreview() {
    PingogramPreview { EncryptionKeyImage(SecretChatPreviewData.keyHash) }
}
