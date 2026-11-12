package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.TextCompositionExample
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat

/** The shared style's sheet with its view model; [onClose] once it closes. */
@Composable
internal fun TextStyleSheetRoute(
    onClose: () -> Unit,
    viewModel: TextStyleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    TextStyleSheet(state, viewModel::onEvent, onClose = { currentOnClose() })
}

/**
 * A shared style of Telegram's AI editor (`docs/DESIGN_SYSTEM.md` §6 "AI text editor"): a [ModalBottomSheet] that
 * opens expanded and scrolls, at most `contentMaxWidth`: the AI badge, the style's title in `titleLargeEmphasized`
 * (a heading) over "Custom style · used by 1,204 people", its example before and after on `surfaceContainerHigh` with
 * `large` corners, how many custom styles the account may add as information, why Telegram refused in `error` (a
 * polite live region), a `LinearWavyProgressIndicator` while it answers, then "Close" and "Add style" (or "Remove
 * style" in `error` once added). Loading shows a `DelayedLoadingIndicator`; a style not found says so with "Try
 * again". Nothing is added before the tap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TextStyleSheet(
    state: TextStyleUiState,
    onEvent: (TextStyleEvent) -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Loading, a failure and the style crossfade in place.
            StateCrossfade(
                targetState = state,
                modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
                contentAlignment = Alignment.TopCenter,
                contentKey = { it::class },
                label = "textStyle",
            ) { shown ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    when (shown) {
                        TextStyleUiState.Loading -> DelayedLoadingIndicator()
                        is TextStyleUiState.Failed -> StyleFailed(shown, onEvent, onClose)
                        is TextStyleUiState.Content -> StyleContent(shown, onEvent, onClose)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StyleContent(
    state: TextStyleUiState.Content,
    onEvent: (TextStyleEvent) -> Unit,
    onClose: () -> Unit,
) {
    val style = state.style
    AiBadge()
    Text(
        text = style.title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLargeEmphasized.userContent,
        textAlign = TextAlign.Center,
    )
    Text(
        text =
            if (style.installCount > 0) {
                pluralStringResource(
                    R.plurals.ai_style_used_by,
                    style.installCount,
                    NumberFormat.getIntegerInstance(Locale.current.platformLocale).format(style.installCount),
                )
            } else {
                stringResource(R.string.ai_style_custom)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    style.example?.let { StyleExample(it) }
    state.limit?.let { limit ->
        Text(
            text = pluralStringResource(R.plurals.ai_style_limit, limit, limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Telegram's refusal grows in above the buttons rather than pushing them down at once.
    val lastError = remember { arrayOfNulls<DomainError>(1) }
    state.error?.let { lastError[0] = it }
    GrowingVisibility(visible = state.error != null) {
        lastError[0]?.let { error ->
            Text(
                text = errorText(error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (state.busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    StyleButtons(state, onEvent, onClose)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StyleButtons(
    state: TextStyleUiState.Content,
    onEvent: (TextStyleEvent) -> Unit,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.ai_style_close))
        }
        // "Add style" and "Remove style" crossfade in place once Telegram took the change.
        StateCrossfade(targetState = state, contentKey = { it.isAdded }, label = "textStyleAction") { shown ->
            StyleAction(shown, onEvent)
        }
    }
}

@Composable
private fun StyleAction(
    state: TextStyleUiState.Content,
    onEvent: (TextStyleEvent) -> Unit,
) {
    when {
        !state.isAdded -> {
            Button(
                onClick = { onEvent(TextStyleEvent.Add) },
                enabled = !state.busy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.ai_style_add), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }

        // A style the user made can only be deleted, which Pingogram does not offer.
        !state.style.isCreator -> {
            TextButton(
                onClick = { onEvent(TextStyleEvent.Remove) },
                enabled = !state.busy,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.ai_style_remove))
            }
        }
    }
}

/** The style on a short English text: "Before" and "After" in `labelMedium` over the texts. */
@Composable
private fun StyleExample(example: TextCompositionExample) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            ExampleText(stringResource(R.string.ai_style_before), example.source.text)
            ExampleText(stringResource(R.string.ai_style_after), example.result.text)
        }
    }
}

@Composable
private fun ExampleText(
    label: String,
    text: String,
) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text, style = MaterialTheme.typography.bodyMedium.userContent)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StyleFailed(
    state: TextStyleUiState.Failed,
    onEvent: (TextStyleEvent) -> Unit,
    onClose: () -> Unit,
) {
    Text(
        text = stringResource(R.string.ai_style_not_found),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
    Text(
        text = errorText(state.error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            onClick = onClose,
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.ai_style_close)) }
        Button(onClick = { onEvent(TextStyleEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.ai_style_try_again))
        }
    }
}
