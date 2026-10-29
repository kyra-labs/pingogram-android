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
import dev.kyralabs.pingogram.core.ui.theme.AvatarColors
import dev.kyralabs.pingogram.core.ui.theme.avatarPlaceholderColors
import dev.kyralabs.pingogram.core.ui.theme.avatarPlaceholderColorsOnSelection
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.snapWhenReducedMotion
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
 *   circle in one of the four avatar color pairs, chosen by [id], so a chat looks the same everywhere. Circles: a
 *   person's picture reads best in a plain circle, and one silhouette per list reads calmer than one per chat. On a
 *   selected or open row
 *   ([onSelection]) the placeholder takes `primary`, which stands out from the row's `secondaryContainer`.
 * - **Online**: a dot in `MaterialTheme.pingogramColors.online` at the bottom end, set off from the avatar by a ring of
 *   [onlineRingColor]. It grows in and shrinks away on the motion scheme's fast spatial spring.
 * - **Selected**: the avatar's shape morphs into a [MaterialShapes] burst in `primary` with a check in `onPrimary`
 *   ([avatarSelection]) on the motion scheme's default spatial spring, e.g. for a chat selected in the chat list.
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
 * @param selected shows the selected look instead of the avatar.
 * @param onSelection the avatar sits on a selected or open row, whose `secondaryContainer` would swallow a
 *   container-colored placeholder.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    selected: Boolean = false,
    onSelection: Boolean = false,
) {
    val fileId = photo?.small?.id
    // From 0 while the photo loads to 1 once it shows; starts over for another photo.
    val photoAlpha = if (fileId != null) remember(fileId) { Animatable(0f) } else null
    val colors = MaterialTheme.colorScheme
    val placeholderColors =
        remember(colors, id, onSelection) {
            if (onSelection) colors.avatarPlaceholderColorsOnSelection() else colors.avatarPlaceholderColors(id)
        }
    // The check's painter is built once the avatar is first selected: most never are, and a list would build one for
    // each row it brings into view. Kept from then on, for the morph back.
    val everSelected = remember { SelectionSeen() }
    if (selected) everSelected.value = true
    val check = if (everSelected.value) painterResource(R.drawable.ic_check) else null
    Box(
        modifier
            .size(size)
            .avatarSemantics(contentDescription)
            // Outside the online dot, which fades out with the avatar.
            .avatarSelection(
                selected = selected,
                avatarShape = MaterialShapes.Circle,
                mark = SelectedMark(check, colors.primary, colors.onPrimary),
                animationSpec = snapWhenReducedMotion(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ).onlineIndicator(
                online = isOnline,
                dotColor = MaterialTheme.pingogramColors.online,
                ringColor = onlineRingColor,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ),
    ) {
        AvatarPlaceholder(
            name = name,
            colors = placeholderColors,
            size = size,
            icon = placeholderIcon,
            photoAlpha = photoAlpha,
            modifier = Modifier.matchParentSize().fadingOutAs(photoAlpha),
        )
        if (fileId != null && photoAlpha != null) {
            AvatarPhoto(fileId = fileId, size = size, alpha = photoAlpha, modifier = Modifier.matchParentSize())
        }
    }
}

/** Whether an avatar has been selected; a plain field, as nothing observes it. */
private class SelectionSeen {
    var value = false
}

/**
 * Fades the placeholder out as the photo fades in ([photoAlpha] from 0 to 1), so its colors do not show around the
 * photo's antialiased edge. Read in the draw phase.
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

/**
 * The placeholder's circle with the initials of [name], or [icon]. Behind a photo ([photoAlpha]) the initials are
 * drawn by [lazyInitials], which lays them out only while the photo does not cover them yet.
 */
@Suppress("LongParameterList") // The name, its colors, size and icon, the photo fading in over it, the modifier.
@Composable
private fun AvatarPlaceholder(
    name: String,
    colors: AvatarColors,
    size: Dp,
    @DrawableRes icon: Int?,
    photoAlpha: Animatable<Float, AnimationVector1D>?,
    modifier: Modifier,
) {
    val initials = remember(name) { avatarInitials(name) }
    val showsInitials = icon == null && initials.isNotEmpty()
    // The photo's fade-in, where the initials sit behind a photo: lazyInitials draws them.
    val behindPhoto = photoAlpha?.takeIf { showsInitials }
    val background = modifier.background(colors.container, CircleShape)
    Box(
        if (behindPhoto != null) {
            background.lazyInitials(initials, colors.content, rememberInitialsStyle(size), behindPhoto)
        } else {
            background
        },
        contentAlignment = Alignment.Center,
    ) {
        when {
            behindPhoto != null -> {
                Unit
            }

            showsInitials -> {
                Text(
                    text = initials,
                    color = colors.content,
                    maxLines = 1,
                    softWrap = false,
                    style = rememberInitialsStyle(size),
                )
            }

            else -> {
                Icon(
                    painter = painterResource(icon ?: R.drawable.ic_person_filled),
                    contentDescription = null,
                    modifier = Modifier.size(size * ICON_SIZE_RATIO),
                    tint = colors.content,
                )
            }
        }
    }
}

/**
 * The largest style of the type scale whose size is at most [INITIALS_SIZE_RATIO] of the avatar's, so two initials fit
 * the placeholder circle: `titleMedium` for 40–54 dp, `titleLarge` for 55–59 dp. Its size is taken in dp: the avatar
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

// Two initials at about 40% of the avatar fit inside the placeholder circle.
private const val INITIALS_SIZE_RATIO = 0.4f
private const val ICON_SIZE_RATIO = 0.55f

/**
 * Avatar variants for previews and screenshot tests: initials in every color pair, a person and an icon placeholder,
 * photos, online dots, selected avatars, a placeholder on a selected row, and the sizes the type scale steps through.
 * Photos are a solid color, which Coil shows in inspection mode (previews) instead of loading from TDLib.
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
                Avatar(photo = null, name = "Ada Lovelace", id = 9, size = medium, selected = true)
                Avatar(photo = null, name = "Émile Zola", id = 1, size = medium, selected = true)
                Avatar(photo = SamplePhoto, name = "Ada Lovelace", id = 10, size = medium, selected = true)
                Avatar(photo = null, name = "Ada Lovelace", id = 9, size = medium, isOnline = true, selected = true)
                Avatar(photo = null, name = "Ada Lovelace", id = 9, size = medium, onSelection = true)
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
