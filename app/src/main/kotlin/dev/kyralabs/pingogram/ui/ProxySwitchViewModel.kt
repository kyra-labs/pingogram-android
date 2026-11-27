package dev.kyralabs.pingogram.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.data.repository.ProxyAutoSwitch
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The proxies Pingogram switched to by itself ([ProxyAutoSwitch]), for the app shell's snackbar. */
@HiltViewModel
class ProxySwitchViewModel
    @Inject
    constructor(
        autoSwitch: ProxyAutoSwitch,
    ) : ViewModel() {
        val switched: Flow<SavedProxy> = autoSwitch.switched
    }

/**
 * "Switched to proxy.example.org:443" in [snackbars] each time Pingogram switches to a working proxy by itself
 * (`REQUIREMENTS.md` FR-X27): the proxy's name, else its address. Composed once, in the app shell, as the switch
 * happens on any screen.
 */
@Composable
internal fun ProxySwitchNotices(
    snackbars: SnackbarHostState,
    viewModel: ProxySwitchViewModel = hiltViewModel(),
) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel, snackbars) {
        viewModel.switched.collect { saved ->
            val shown = saved.name.ifBlank { "${saved.proxy.server}:${saved.proxy.port}" }
            snackbars.showSnackbar(resources.getString(R.string.proxy_switched, shown))
        }
    }
}
