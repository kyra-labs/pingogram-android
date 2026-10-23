package dev.kyralabs.pingogram.feature.settings.ui.stickers.set

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.sticker.StickerCover
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickerSetSheetPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.stickers.shareStickerSet
import dev.kyralabs.pingogram.feature.settings.ui.stickers.stickerCount
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The destination of a sticker set (`StickerSetRoute`): [StickerSetSheet] for its [StickerSetSheetViewModel], over the
 * screen it was opened from. [onClose] leaves the destination once the sheet closed; [share] hands the set's link to
 * Android's share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerSetSheetRoute(
    onClose: () -> Unit,
    viewModel: StickerSetSheetViewModel = hiltViewModel(),
    share: Context.(name: String) -> Unit = Context::shareStickerSet,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentOnClose by rememberUpdatedState(onClose)
    StickerSetSheet(
        state = state,
        onEvent = viewModel::onEvent,
        onShare = { context.share(it) },
        onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnClose() } },
        onDismiss = { currentOnClose() },
        sheetState = sheetState,
    )
}

/** The sheet of a sticker set (`docs/DESIGN_SYSTEM.md` §6 "Sticker set sheet"): a `ModalBottomSheet`, expanded. */
@Suppress("LongParameterList") // The sheet's state and events, its three ways out, and its state.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerSetSheet(
    state: StickerSetSheetUiState,
    onEvent: (StickerSetSheetEvent) -> Unit,
    onShare: (name: String) -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        StickerSetContent(state, onEvent, onShare, onClose)
    }
}

/**
 * What the sheet shows for [state], each step crossfading on the effects springs while the sheet resizes on the
 * default spatial spring: the delayed loading indicator, the set, or why it cannot be shown.
 */
@Composable
internal fun StickerSetContent(
    state: StickerSetSheetUiState,
    onEvent: (StickerSetSheetEvent) -> Unit,
    onShare: (name: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = MaterialTheme.spacing.large),
        transitionSpec = {
            (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() })
        },
        // A new step crossfades; the same step changing (adding, an error) does not.
        contentKey = { it::class },
        contentAlignment = Alignment.TopCenter,
        label = "stickerSetStep",
    ) { step ->
        Box(Modifier.fillMaxWidth().widthIn(max = MaterialTheme.sizes.contentMaxWidth), Alignment.TopCenter) {
            when (step) {
                StickerSetSheetUiState.Loading -> {
                    DelayedLoadingIndicator(Modifier.heightIn(min = MaterialTheme.sizes.illustrationMaxSize))
                }

                is StickerSetSheetUiState.Content -> {
                    StickerSetDetails(step, onEvent, onShare)
                }

                is StickerSetSheetUiState.Failed -> {
                    StickerSetFailed(step, onEvent, onClose)
                }
            }
        }
    }
}

/**
 * The set: its title (a heading, user content) over how many stickers it has, its stickers in a grid (animated ones
 * playing within the app's limit), then why a change failed, the progress, "Share" and "Add stickers" or "Remove".
 */
@Composable
private fun StickerSetDetails(
    state: StickerSetSheetUiState.Content,
    onEvent: (StickerSetSheetEvent) -> Unit,
    onShare: (name: String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(MaterialTheme.sizes.panelSticker),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = spacing.medium),
        ) {
            item(key = TITLE, span = { GridItemSpan(maxLineSpan) }, contentType = TITLE) { SetTitle(state) }
            itemsIndexed(state.stickers, key = { index, _ -> index }, contentType = { _, _ -> STICKER }) { _, sticker ->
                SetSticker(sticker)
            }
        }
        Column(
            Modifier.padding(horizontal = spacing.large).padding(top = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            state.changeError?.let { ChangeError(it) }
            if (state.isChanging) {
                val changing =
                    stringResource(
                        if (state.isInstalled) R.string.sticker_set_removing else R.string.sticker_set_adding,
                    )
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = changing })
            }
            SetActions(state, onEvent, onShare)
        }
    }
}

@Composable
private fun SetTitle(state: StickerSetSheetUiState.Content) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.small)
            .padding(bottom = MaterialTheme.spacing.medium)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Text(
            text = state.title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized.userContent,
        )
        Text(
            text = stickerCount(state.stickers.size),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A sticker of the set, as large as the sticker panel's: an image for screen readers ("😀 Sticker"). */
@Composable
private fun SetSticker(sticker: Sticker) {
    val description =
        if (sticker.emoji.isBlank()) {
            stringResource(UiR.string.preview_sticker)
        } else {
            stringResource(UiR.string.preview_sticker_emoji, sticker.emoji)
        }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(MaterialTheme.spacing.extraSmall)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            },
        contentAlignment = Alignment.Center,
    ) {
        StickerCover(sticker, MaterialTheme.sizes.panelSticker - MaterialTheme.spacing.small, animate = true)
    }
}

/** "Share" (a `TextButton`) at the start; "Add stickers" (a default `Button`) or "Remove" (`error`) at the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetActions(
    state: StickerSetSheetUiState.Content,
    onEvent: (StickerSetSheetEvent) -> Unit,
    onShare: (name: String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.name.isNotEmpty()) {
            TextButton(onClick = { onShare(state.name) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.sticker_set_share), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Box {}
        }
        if (state.isInstalled) {
            TextButton(
                onClick = { onEvent(StickerSetSheetEvent.Remove) },
                enabled = !state.isChanging,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.sticker_set_remove), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = { onEvent(StickerSetSheetEvent.Add) },
                enabled = !state.isChanging,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.sticker_set_add), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/** Why adding or removing failed, in `error`, said by screen readers as it appears. */
@Composable
private fun ChangeError(error: DomainError) {
    val text = errorMessage(LocalResources.current, error, isDebuggable())
    if (text.isEmpty()) return
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Why the set cannot be shown: a `Cookie4Sided` badge on `errorContainer`, the error, "Close" and "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StickerSetFailed(
    state: StickerSetSheetUiState.Failed,
    onEvent: (StickerSetSheetEvent) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val badgeSize = MaterialTheme.sizes.headlineBadge
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Box(
            Modifier
                .size(badgeSize)
                .background(colors.errorContainer, MaterialShapes.Cookie4Sided.toShape()),
            Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_mood),
                contentDescription = null,
                modifier = Modifier.size(badgeSize / 2),
                tint = colors.onErrorContainer,
            )
        }
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text =
                    stringResource(
                        if (state.error == DomainError.NotFound) {
                            R.string.sticker_set_not_found_title
                        } else {
                            R.string.sticker_set_failed_title
                        },
                    ),
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                text =
                    if (state.error == DomainError.NotFound) {
                        stringResource(R.string.sticker_set_not_found_text)
                    } else {
                        errorMessage(LocalResources.current, state.error, isDebuggable())
                    },
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FailedActions(state.canRetry, onRetry = { onEvent(StickerSetSheetEvent.Retry) }, onClose = onClose)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FailedActions(
    canRetry: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.sticker_set_close), style = MaterialTheme.typography.labelLarge)
        }
        if (canRetry) {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(R.string.sticker_set_try_again),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

private const val TITLE = "title"
private const val STICKER = "sticker"

@PingogramPreviews
@Composable
private fun StickerSetContentPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.notAdded, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentAddedPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.added, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentAddingPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.adding, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentNotFoundPreview() {
    PingogramPreview {
        StickerSetContent(StickerSetSheetPreviewStates.notFound, onEvent = {}, onShare = {}, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun StickerSetContentLoadingPreview() {
    PingogramPreview { StickerSetContent(StickerSetSheetUiState.Loading, onEvent = {}, onShare = {}, onClose = {}) }
}
