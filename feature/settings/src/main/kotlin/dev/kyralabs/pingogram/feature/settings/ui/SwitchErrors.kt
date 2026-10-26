package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Shows what the page's switches could not save as snackbars in [snackbarHostState]. */
@Composable
internal fun SwitchErrors(
    snackbarHostState: SnackbarHostState,
    vararg errors: Flow<DomainError>,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(snackbarHostState, *errors) {
        merge(*errors).collect { error ->
            launch { snackbarHostState.showSnackbar(errorMessage(resources, error, showErrorDetails)) }
        }
    }
}
