package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.launch

/** The bookmark sheet of [viewModel] while it shows, and what it says in the conversation's snackbars. */
@Composable
internal fun BookmarkSheetHost(
    viewModel: BookmarkSheetViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val details = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            // Snackbars queue up; later effects must not wait for them.
            launch {
                val text =
                    when (effect) {
                        BookmarkEffect.Saved -> resources.getString(R.string.bookmark_saved)
                        BookmarkEffect.Removed -> resources.getString(R.string.bookmark_removed)
                        is BookmarkEffect.ShowError -> errorMessage(resources, effect.error, details)
                    }
                snackbarHostState.showSnackbar(text)
            }
        }
    }
    sheet?.let { BookmarkSheet(it, viewModel::onEvent) }
}
