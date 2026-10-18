package dev.kyralabs.pingogram.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.avatarPlaceholderColors
import dev.kyralabs.pingogram.core.ui.theme.avatarPlaceholderPolygon
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * A user's or chat's avatar (`docs/DESIGN_SYSTEM.md` §2.2, §4, §8): its [photo] in a circle, or a placeholder while the
 * photo loads, when there is none, and when it cannot be loaded.
 *
 * - **Photo**: `photo.small` (160 × 160 px), loaded from TDLib's files by Coil ([TdlibFileImage]) at the avatar's
 *   [size] and faded in on the motion scheme's default effects spring; shown at once when Coil's memory cache has it,
 *   e.g. when a list scrolls back.
 * - **Placeholder**: the initials of [name] (or [placeholderIcon], or a person when [name] has no initials) on a
 *   [MaterialShapes] polygon in one of the seven avatar color pairs. Both are chosen by [id], so a chat looks the same
 *   everywhere, and they cycle with coprime lengths, so a shape does not always come with the same colors.
 * - **Online**: a dot in `MaterialTheme.pingogramColors.online` at the bottom end, set off from the avatar by a ring of
 *   [onlineRingColor]. It grows in and shrinks away on the motion scheme's fast spatial spring.
 *
 * Decorative by default, since list rows merge their own semantics (name, preview, online state): screen readers skip
 * it, initials included. Pass [contentDescription] where the avatar stands alone, e.g. in a profile header.
 *
 * The avatar has a fixed [size], so its initials follow that size rather than the font scale. Per avatar it runs no
 * effect besides Coil's own request, and animations are read in the draw phase only (`docs/PERFORMANCE.md` §4).
 *
 * @param photo the photo to show, or null for the placeholder alone.
 * @param name the user's or chat's name, for the initials.
 * @param id the TDLib user or chat id, which chooses the placeholder's shape and colors. A private chat's id is its
 *   user's id, so both look the same.
 * @param size the avatar's diameter, e.g. `MaterialTheme.sizes.avatarMedium`; also the size the photo is decoded at.
 * @param isOnline shows the online dot.
 * @param placeholderIcon a Material Symbols drawable shown on the placeholder instead of the initials, e.g. for Saved
 *   Messages or a deleted account.
 * @param contentDescription what the avatar shows, for screen readers; null keeps it decorative.
 * @param onlineRingColor the color behind the avatar (a row's container color), which sets the online dot off.
 */
@Composable
fun Avatar(
    photo: ProfilePhoto?,
    name: String,
    id: Long,
    size: Dp,
    modifier: Modifier = Modifier,
    isOnline: Boolean = false,
    @DrawableRes placeholderIcon: Int? = null,
    contentDescription: String? = null,
    onlineRingColor: Color = MaterialTheme.colorScheme.surface,
) {
    val fileId = photo?.small?.id
    // From 0 while the photo loads to 1 once it shows; starts over for another photo.
    val photoAlpha = if (fileId != null) remember(fileId) { Animatable(0f) } else null
    Box(
        modifier
            .size(size)
            .avatarSemantics(contentDescription)
            .onlineIndicator(
                online = isOnline,
                dotColor = MaterialTheme.pingogramColors.online,
                ringColor = onlineRingColor,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ),
    ) {
        AvatarPlaceholder(
            name = name,
            id = id,
            size = size,
            icon = placeholderIcon,
            modifier = Modifier.matchParentSize().fadingOutAs(photoAlpha),
        )
        if (fileId != null && photoAlpha != null) {
            AvatarPhoto(fileId = fileId, size = size, alpha = photoAlpha, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * Fades the placeholder out as the photo fades in ([photoAlpha] from 0 to 1), so the polygon's corners do not show
 * around the round photo. Read in the draw phase.
 */
private fun Modifier.fadingOutAs(photoAlpha: Animatable<Float, AnimationVector1D>?): Modifier =
    if (photoAlpha == null) this else graphicsLayer { alpha = 1f - photoAlpha.value }

@Composable
private fun AvatarPhoto(
    fileId: Int,
    size: Dp,
    alpha: Animatable<Float, AnimationVector1D>,
    modifier: Modifier,
) {
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val request =
        remember(context, fileId, sizePx) {
            ImageRequest
                .Builder(context)
                .data(TdlibFileImage(fileId))
                .size(sizePx)
                .build()
        }
    val scope = rememberCoroutineScope()
    val fadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier =
            modifier.graphicsLayer {
                this.alpha = alpha.value
                shape = CircleShape
                clip = true
            },
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            // Coil's own crossfade is a tween (DESIGN_SYSTEM.md §5), so the fade is ours. Images that were in memory
            // already show at once, before the next frame, so lists do not flicker while scrolling.
            val loaded = state.result.dataSource
            if (loaded == DataSource.DISK || loaded == DataSource.NETWORK) {
                scope.launch { alpha.animateTo(1f, fadeIn) }
            } else {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { alpha.snapTo(1f) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AvatarPlaceholder(
    name: String,
    id: Long,
    size: Dp,
    @DrawableRes icon: Int?,
    modifier: Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme, id) { colorScheme.avatarPlaceholderColors(id) }
    // toShape() remembers the shape per polygon.
    val shape = avatarPlaceholderPolygon(id).toShape()
    val initials = remember(name) { avatarInitials(name) }
    Box(modifier.background(colors.container, shape), contentAlignment = Alignment.Center) {
        if (icon == null && initials.isNotEmpty()) {
            Text(
                text = initials,
                color = colors.content,
                maxLines = 1,
                softWrap = false,
                style = rememberInitialsStyle(size),
            )
        } else {
            Icon(
                painter = painterResource(icon ?: R.drawable.ic_person_filled),
                contentDescription = null,
                modifier = Modifier.size(size * ICON_SIZE_RATIO),
                tint = colors.content,
            )
        }
    }
}

/**
 * The largest style of the type scale whose size is at most [INITIALS_SIZE_RATIO] of the avatar's, so two initials fit
 * the placeholder polygon: `titleMedium` for 40–54 dp, `titleLarge` for 55–59 dp. Its size is taken in dp: the avatar
 * does not grow with the font scale, so neither do its initials. Its font keeps the style's optical size, which
 * matches.
 */
@Composable
private fun rememberInitialsStyle(avatarSize: Dp): TextStyle {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    return remember(typography, density, avatarSize) {
        val style = typography.initialsStyle(avatarSize)
        val fontSize = with(density) { Dp(style.fontSize.value).toSp() }
        style.copy(fontSize = fontSize, lineHeight = fontSize)
    }
}

private fun Typography.initialsStyle(avatarSize: Dp): TextStyle {
    val largest = avatarSize.value * INITIALS_SIZE_RATIO
    return listOf(
        displayLarge,
        displayMedium,
        displaySmall,
        headlineLarge,
        headlineMedium,
        headlineSmall,
        titleLarge,
        titleMedium,
        titleSmall,
        labelMedium,
        labelSmall,
    ).firstOrNull { style -> style.fontSize.value <= largest } ?: labelSmall
}

private fun Modifier.avatarSemantics(contentDescription: String?): Modifier =
    clearAndSetSemantics {
        if (contentDescription != null) {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }

// Two initials at about 40% of the avatar fit inside every placeholder polygon.
private const val INITIALS_SIZE_RATIO = 0.4f
private const val ICON_SIZE_RATIO = 0.55f

/**
 * Avatar variants for previews and screenshot tests: initials in every color pair and placeholder shape, a person and
 * an icon placeholder, photos, online dots, and the sizes the type scale steps through. Photos are a solid color,
 * which Coil shows in inspection mode (previews) instead of loading from TDLib.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun AvatarShowcase(modifier: Modifier = Modifier) {
    val photoColor = MaterialTheme.colorScheme.tertiary
    val previewHandler = remember(photoColor) { AsyncImagePreviewHandler { ColorImage(photoColor.toArgb()) } }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewHandler) {
        Column(
            modifier = modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            val medium = MaterialTheme.sizes.avatarMedium
            ShowcaseRow {
                SampleNames.take(SAMPLES_PER_ROW).forEachIndexed { index, name ->
                    Avatar(photo = null, name = name, id = index.toLong(), size = medium)
                }
            }
            ShowcaseRow {
                Avatar(photo = null, name = SampleNames.last(), id = SampleNames.lastIndex.toLong(), size = medium)
                Avatar(photo = null, name = "", id = 7, size = medium)
                Avatar(photo = null, name = "Saved", id = 8, size = medium, placeholderIcon = R.drawable.ic_chat_filled)
                Avatar(photo = null, name = "Ada Lovelace", id = 9, size = medium, isOnline = true)
                Avatar(photo = SamplePhoto, name = "Ada Lovelace", id = 10, size = medium)
                Avatar(photo = SamplePhoto, name = "Ada Lovelace", id = 11, size = medium, isOnline = true)
            }
            ShowcaseRow {
                listOf(ProfileSize, medium, BetweenSize, MaterialTheme.sizes.avatarSmall, TinySize).forEach { size ->
                    Avatar(photo = null, name = "Wanda Maximoff", id = 4, size = size, isOnline = true)
                }
            }
        }
    }
}

@Composable
private fun ShowcaseRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private const val SAMPLES_PER_ROW = 6

// Sizes around the avatar size tokens: a profile header, between the two tokens, and below them.
private val ProfileSize = 96.dp
private val BetweenSize = 48.dp
private val TinySize = 32.dp

// Latin, an accent, Cyrillic, Greek, Han, an emoji and wide letters.
private val SampleNames =
    listOf("Ada Lovelace", "Émile Zola", "Мария Кюри", "Αλέξης Ζορμπάς", "李雷", "😀 Smiles", "Wanda Maximoff")

private val SamplePhoto =
    ProfilePhoto(small = FileRef(id = 1, size = 0), big = FileRef(id = 2, size = 0), minithumbnail = null)

@PingogramPreviews
@Composable
private fun AvatarPreview() {
    PingogramPreview { AvatarShowcase() }
}
