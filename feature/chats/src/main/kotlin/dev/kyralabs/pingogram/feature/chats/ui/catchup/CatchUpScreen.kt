package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Catch-up's screen: hoists [CatchUpViewModel], opens a chat with [onOpenChat] and shows what did not work in a
 * snackbar; [onBack] closes it (close, "Back to chats", system back).
 */
@Composable
internal fun CatchUpRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatchUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CatchUpEffect.OpenChat -> {
                    onOpenChat(effect.chatId)
                }

                // Snackbars queue up; later effects must not wait for them.
                is CatchUpEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    CatchUpScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Catch-up (`docs/DESIGN_SYSTEM.md` §6 "Catch-up", the owner's board 32): a center-aligned top bar with close, "Catch
 * up" over "3 of 12 · about 4 min left" (a polite live region, so each new card is heard) and the settings button;
 * then the segmented progress ([CatchUpProgress]) and the card stack ([CatchUpCards]), or once every chat is handled
 * "All caught up" ([CatchUpDone]), or with nothing to catch up on its empty state ([CatchUpEmpty]). The stages
 * crossfade on the effects springs. The settings sheet ([CatchUpSettingsSheet]) includes muted chats and channels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatchUpScreen(
    state: CatchUpUiState,
    onEvent: (CatchUpEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { CatchUpTopBar(state.stage, onEvent, onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            StateCrossfade(
                targetState = state.stage,
                contentKey = { it::class },
                label = "catchUpStage",
                modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxSize(),
            ) { stage ->
                when (stage) {
                    CatchUpStage.Loading -> CatchUpLoading()
                    CatchUpStage.Empty -> CatchUpEmpty(onBack)
                    is CatchUpStage.Cards -> CatchUpCards(stage, onEvent)
                    is CatchUpStage.Done -> CatchUpDone(stage, onEvent, onBack)
                }
            }
        }
    }
    if (state.showsSettings) {
        CatchUpSettingsSheet(
            includesMuted = state.includesMuted,
            onIncludesMuted = { onEvent(CatchUpEvent.SetIncludesMuted(it)) },
            onDismiss = { onEvent(CatchUpEvent.ShowSettings(false)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatchUpTopBar(
    stage: CatchUpStage,
    onEvent: (CatchUpEvent) -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.catch_up_title),
                    modifier = Modifier.semantics { heading() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                if (stage is CatchUpStage.Cards) CatchUpPosition(stage)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.catch_up_close))
            }
        },
        actions = {
            IconButton(onClick = { onEvent(CatchUpEvent.ShowSettings(true)) }, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_tune), stringResource(R.string.catch_up_settings))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** "3 of 12 · about 4 min left", heard on each new card. */
@Composable
private fun CatchUpPosition(stage: CatchUpStage.Cards) {
    val resources = LocalResources.current
    val left = resources.getQuantityString(R.plurals.catch_up_minutes_left, stage.minutesLeft, stage.minutesLeft)
    Text(
        text = stringResource(R.string.catch_up_position, stage.position, stage.total, left),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun CatchUpLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
}

/** The progress and the stack of cards. */
@Composable
private fun CatchUpCards(
    stage: CatchUpStage.Cards,
    onEvent: (CatchUpEvent) -> Unit,
) {
    Column(
        // Above the keyboard while the reply field has it.
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        CatchUpProgress(stage.position, stage.total, Modifier.fillMaxWidth())
        CatchUpStack(stage, onEvent, Modifier.weight(1f).fillMaxWidth())
    }
}
