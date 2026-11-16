package dev.kyralabs.pingogram.feature.chat.ui.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The welcome pack at the top of the conversation's sheet (the owner's board 6), inset as the pinned bar is and
 * growing in and shrinking away as it does. In full: a card on `surfaceContainerHigh` with `large` corners, "Welcome
 * message · only you see this" (`labelMedium` `primary`, a heading) after the megaphone symbol, then each message, its
 * photo (`sizes.avatarLarge`, `large` corners, dimmed in dark theme as media) beside its text (`bodyMedium`, user
 * content, with its formatting), scrolling past `sizes.mediaMaxHeight`, and "Got it" (`FilledTonalButton`) at the
 * end, which collapses it. Collapsed: one line, "Welcome message" over the first message's start, a tap opening it
 * again.
 * Nothing while there is no [pack].
 */
@Composable
internal fun WelcomePackSlot(
    pack: WelcomePackUi?,
    onEvent: (WelcomePackEvent) -> Unit,
    padding: PaddingValues,
) {
    val motion = MaterialTheme.motionScheme
    val last = remember { mutableStateOf(pack) }
    SideEffect { if (pack != null) last.value = pack }
    val shown = pack ?: last.value
    val direction = LocalLayoutDirection.current
    val small = MaterialTheme.spacing.small
    AnimatedVisibility(
        visible = pack != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        shown?.let {
            WelcomePack(
                pack = it,
                onEvent = onEvent,
                modifier =
                    Modifier.padding(
                        start = padding.calculateStartPadding(direction) + small,
                        top = padding.calculateTopPadding() + small,
                        end = padding.calculateEndPadding(direction) + small,
                    ),
            )
        }
    }
}

@Composable
internal fun WelcomePack(
    pack: WelcomePackUi,
    onEvent: (WelcomePackEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        AnimatedContent(
            targetState = pack.collapsed,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "welcomePack",
        ) { collapsed ->
            if (collapsed) {
                CollapsedPack(pack) { onEvent(WelcomePackEvent.Expand) }
            } else {
                FullPack(pack) { onEvent(WelcomePackEvent.Collapse) }
            }
        }
    }
}

@Composable
private fun FullPack(
    pack: WelcomePackUi,
    onGotIt: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        PackLabel(stringResource(R.string.welcome_pack_label), Modifier.semantics { heading() })
        Column(
            modifier =
                Modifier
                    .heightIn(
                        max = MaterialTheme.sizes.mediaMaxHeight,
                    ).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            pack.messages.forEach { WelcomeMessageRow(it) }
        }
        FilledTonalButton(
            onClick = onGotIt,
            modifier = Modifier.align(Alignment.End),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.welcome_pack_got_it))
        }
    }
}

@Composable
private fun CollapsedPack(
    pack: WelcomePackUi,
    onExpand: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val label = stringResource(R.string.welcome_pack_open)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = label, role = Role.Button, onClick = onExpand)
                .padding(horizontal = spacing.medium, vertical = spacing.small),
    ) {
        PackLabel(stringResource(R.string.welcome_pack_title))
        Text(
            pack.messages
                .first()
                .text.text,
            style = MaterialTheme.typography.bodyMedium.userContent,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The megaphone symbol and [text] in `labelMedium` `primary`. */
@Composable
private fun PackLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(UiR.drawable.ic_campaign),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WelcomeMessageRow(item: WelcomeItem) {
    val styles = rememberFormattedTextStyles()
    val text = remember(item.text, styles) { item.text.toAnnotatedString(styles) }
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        item.photo?.let { WelcomePhoto(it) }
        if (item.text.text.isNotEmpty()) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.userContent)
        }
    }
}

/** A welcome message's photo, the smallest size that fills [MaterialTheme.sizes.avatarLarge]; decorative. */
@Composable
private fun WelcomePhoto(photo: Photo) {
    val size = MaterialTheme.sizes.avatarLarge
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val fileId =
        remember(photo, sizePx) {
            val bySide = photo.sizes.sortedBy { minOf(it.width, it.height) }
            (bySide.firstOrNull { minOf(it.width, it.height) >= sizePx } ?: bySide.lastOrNull())?.file?.id
        }
    val context = LocalPlatformContext.current
    Surface(
        modifier = Modifier.size(size).clip(MaterialTheme.shapes.large),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (fileId != null) {
            AsyncImage(
                model =
                    remember(context, fileId, sizePx) {
                        ImageRequest
                            .Builder(context)
                            .data(TdlibFileImage(fileId))
                            .size(sizePx)
                            .build()
                    },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
            )
        }
    }
}

internal val welcomePackPreview =
    WelcomePackUi(
        messages =
            persistentListOf(
                WelcomeItem(
                    1,
                    FormattedText("Welcome to Pingogram Beta! Read the pinned rules, then say hi.", persistentListOf()),
                    null,
                ),
                WelcomeItem(2, FormattedText("Builds go out every Friday.", persistentListOf()), null),
            ),
        collapsed = false,
    )

@PingogramPreviews
@Composable
private fun WelcomePackPreview() {
    PingogramPreview { WelcomePack(welcomePackPreview, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun WelcomePackCollapsedPreview() {
    PingogramPreview { WelcomePack(welcomePackPreview.copy(collapsed = true), onEvent = {}) }
}
