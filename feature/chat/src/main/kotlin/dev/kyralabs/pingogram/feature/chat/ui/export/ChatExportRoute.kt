package dev.kyralabs.pingogram.feature.chat.ui.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.launch

/**
 * The export page of the route's chat: hoists [ChatExportViewModel] and handles what needs the platform. "Choose"
 * opens the Storage Access Framework's folder picker (`ACTION_OPEN_DOCUMENT_TREE`, no storage permission), whose
 * folder goes to the view model; "Open folder" asks an app to show the export's folder through [openFolder]
 * (`ACTION_VIEW`), with a snackbar where none does. [ChatExportEffect]s are snackbars. [onBack] leaves the page.
 */
@Composable
internal fun ChatExportRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    openFolder: Context.(folderUri: String) -> Boolean = Context::openFolder,
    viewModel: ChatExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            tree?.let { viewModel.onEvent(ChatExportEvent.FolderPicked(it.toString())) }
        }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    ChatExportEffect.FolderUnusable -> R.string.chat_export_folder_unusable
                    ChatExportEffect.Cancelled -> R.string.chat_export_cancelled
                }
            // Snackbars queue up; later effects must not wait for them.
            launch { snackbarHostState.showSnackbar(resources.getString(message)) }
        }
    }
    val scope = rememberCoroutineScope()
    ChatExportScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onChooseFolder = { pickFolder.launch(null) },
        onOpenFolder = { uri ->
            if (!openFolder(context, uri)) {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.chat_export_no_folder_app)) }
            }
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Asks an app to show folder [folderUri] (a document URI); false if none does. */
internal fun Context.openFolder(folderUri: String): Boolean {
    if (folderUri.isEmpty()) return false
    val view =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(folderUri.toUri(), DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        startActivity(view)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
