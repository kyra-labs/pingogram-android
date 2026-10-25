package dev.kyralabs.pingogram.feature.settings.ui.stickers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsBody
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageFrame
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickersPreviewStates
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Settings → Stickers, or its Emoji page ([kind]): hoists [StickersViewModel], shows what it did and its failures in
 * snackbars (archiving and removing with "Undo") and shares a set's link through Android's share sheet. [onOpenList]
 * opens the kind's trending (false) or archived sets (true), [onOpenSet] a set's sheet, [onOpenEmoji] the Emoji page
 * from the stickers'; [onBack] leaves the page.
 */
@Suppress("LongParameterList") // The page's kind, its four ways out, and its hoisted view model.
@Composable
internal fun StickersRoute(
    onOpenList: (archived: Boolean) -> Unit,
    onOpenSet: (setId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    kind: StickerSetKind = StickerSetKind.Stickers,
    onOpenEmoji: () -> Unit = {},
    viewModel: StickersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StickersEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }

                is StickersEffect.Changed -> {
                    launch {
                        val done =
                            if (effect.archived) {
                                R.string.settings_stickers_archived_done
                            } else {
                                R.string.settings_stickers_removed_done
                            }
                        val message = resources.getString(done, effect.title.bidiIsolated())
                        val result =
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = resources.getString(R.string.settings_stickers_undo),
                                withDismissAction = true,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onEvent(StickersEvent.Undo(effect.id, effect.previousOrder))
                        }
                    }
                }
            }
        }
    }
    StickersScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenList = onOpenList,
        onShare = { context.shareStickerSet(it.name, it.kind) },
        onOpenSet = onOpenSet,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        kind = kind,
        onOpenEmoji = onOpenEmoji,
    )
}

/**
 * Stickers (`REQUIREMENTS.md` FR-C5): Trending stickers, Archived stickers and Emoji ([onOpenEmoji]), then the user's
 * sticker sets in the order the sticker panel shows them, each with its cover, title and count, moved by dragging its
 * handle or after a long press (screen readers: "Move up", "Move down"); a tap opens its menu: Show stickers
 * ([onOpenSet]), Share link, Archive, Remove (asked first with an `AlertDialog`). For [kind] Emoji, the same page of
 * the user's custom emoji sets: Trending emoji and Archived emoji, then the sets.
 */
@Suppress("LongParameterList") // A page's state, its events, its ways out, and its hoisted states.
@Composable
internal fun StickersScreen(
    state: StickersUiState,
    onEvent: (StickersEvent) -> Unit,
    onOpenList: (archived: Boolean) -> Unit,
    onShare: (StickerSetItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSet: (setId: Long) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    kind: StickerSetKind = StickerSetKind.Stickers,
    onOpenEmoji: () -> Unit = {},
) {
    val texts = stickerKindTexts(kind)
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    val actions =
        remember(onEvent, onShare, onOpenSet) {
            StickerSetActions(
                onOpen = { onOpenSet(it.id) },
                onShare = onShare,
                onArchive = { onEvent(StickersEvent.Archive(it.id)) },
                onRemove = { removing = it.id },
            )
        }
    SettingsPageFrame(
        title = stringResource(texts.page),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            when (state) {
                StickersUiState.Loading -> {
                    SettingsLoading(
                        description = stringResource(texts.loading),
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is StickersUiState.Error -> {
                    SettingsError(
                        error = state.error,
                        onRetry = { onEvent(StickersEvent.Retry) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is StickersUiState.Content -> {
                    val pages = StickerPages(texts, onOpenList, onOpenEmoji.takeIf { kind == StickerSetKind.Stickers })
                    StickersList(state, onEvent, pages, actions, listState, contentPadding)
                }
            }
        }
    }
    val set = (state as? StickersUiState.Content)?.installed?.firstOrNull { it.id == removing }
    if (set != null) {
        RemoveSetDialog(
            title = set.title,
            text = texts.removeText,
            onConfirm = {
                removing = null
                onEvent(StickersEvent.Remove(set.id))
            },
            onDismiss = { removing = null },
        )
    }
}

/**
 * Asks before removing set [title] ([text] says what that does: it leaves the sticker panel or the emoji sets on every
 * device, and comes back only by its link).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoveSetDialog(
    title: String,
    @StringRes text: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.settings_stickers_remove_title, title.bidiIsolated())) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_stickers_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_stickers_cancel))
            }
        },
    )
}

/**
 * Hands set [name]'s link (`t.me/addstickers/<name>`, `t.me/addemoji/<name>` for a set of custom emoji) to Android's
 * share sheet; nothing where no app takes it.
 */
internal fun Context.shareStickerSet(
    name: String,
    kind: StickerSetKind = StickerSetKind.Stickers,
) {
    val link = "https://t.me/${stickerKindTexts(kind).linkPath}/$name"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
    try {
        startActivity(Intent.createChooser(send, getString(R.string.settings_stickers_share)))
    } catch (_: ActivityNotFoundException) {
        // No app shares text: there is nothing to offer.
    }
}

@PingogramPreviews
@Composable
private fun StickersScreenPreview() {
    PingogramPreview {
        StickersScreen(StickersPreviewStates.content, onEvent = {}, onOpenList = {}, onShare = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun EmojiSetsScreenPreview() {
    PingogramPreview {
        StickersScreen(
            StickersPreviewStates.emoji,
            onEvent = {},
            onOpenList = {},
            onShare = {},
            onBack = {},
            kind = StickerSetKind.Emoji,
        )
    }
}

@PingogramPreviews
@Composable
private fun StickersScreenNoSetsPreview() {
    PingogramPreview {
        StickersScreen(StickersPreviewStates.noSets, onEvent = {}, onOpenList = {}, onShare = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun StickersScreenLoadingPreview() {
    PingogramPreview {
        StickersScreen(StickersUiState.Loading, onEvent = {}, onOpenList = {}, onShare = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun StickersScreenErrorPreview() {
    PingogramPreview {
        StickersScreen(
            StickersUiState.Error(DomainError.Network),
            onEvent = {},
            onOpenList = {},
            onShare = {},
            onBack = {},
        )
    }
}
