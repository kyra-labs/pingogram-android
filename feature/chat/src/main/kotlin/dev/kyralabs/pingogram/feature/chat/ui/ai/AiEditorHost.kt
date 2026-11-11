package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText

/**
 * Hosts the AI editor of the conversation's composer ([AiEditorViewModel]): offers it to the composer's formatting
 * menu ([LocalAiEditor]) where Telegram's AI may be used, shows its sheet, and puts a result the user chose into
 * [composerText] with "Undo" in [snackbarHostState], which brings the text back as it was; around [content].
 */
@Composable
internal fun AiEditorHost(
    viewModel: AiEditorViewModel,
    composerText: ComposerText,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    val available by viewModel.isAvailable.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel, composerText) {
        // After process death the editor opens again on the composer's text, which only the route holds.
        if (viewModel.needsText) viewModel.onEvent(AiEditorEvent.Open(composerText.content))
    }
    LaunchedEffect(viewModel, composerText) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AiEditorEffect.Use -> {
                    composerText.setContent(effect.text)
                    val result =
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.ai_editor_used),
                            actionLabel = resources.getString(R.string.ai_editor_undo),
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) composerText.setContent(effect.original)
                }
            }
        }
    }
    val open: ((FormattedText) -> Unit)? =
        if (available) { text -> viewModel.onEvent(AiEditorEvent.Open(text)) } else null
    CompositionLocalProvider(LocalAiEditor provides open, content = content)
    sheet?.let { AiEditorSheet(it, viewModel::onEvent) }
}
