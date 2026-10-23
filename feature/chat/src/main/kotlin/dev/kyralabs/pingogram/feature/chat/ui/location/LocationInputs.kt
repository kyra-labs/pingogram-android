package dev.kyralabs.pingogram.feature.chat.ui.location

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.geoUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * What the attach menu's "Location" sheet works with, from the conversation's route: [state] reads the
 * [ShareLocationViewModel]'s state (only the sheet reads it, so a better location recomposes nothing else), [onEvent]
 * takes its events, and [canOpenMaps] says whether an app on the device shows `geo:` links.
 */
@Stable
internal class LocationInputs(
    val state: () -> ShareLocationUiState = { ShareLocationUiState.Closed },
    val onEvent: (ShareLocationEvent) -> Unit = {},
    val canOpenMaps: Boolean = true,
)

/**
 * The route's [LocationInputs] for [viewModel]: its state while the screen is started, the screen's start as
 * [ShareLocationEvent.Started] (back from the system settings), and its effects ([ShareLocationEffects]), whose
 * snackbar shows on [snackbarHostState].
 */
@Composable
internal fun rememberLocationInputs(
    viewModel: ShareLocationViewModel,
    snackbarHostState: SnackbarHostState,
): LocationInputs {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ShareLocationEvent.Started)
        onStopOrDispose {}
    }
    ShareLocationEffects(viewModel.effects, snackbarHostState, viewModel::onEvent)
    val context = LocalContext.current
    val canOpenMaps = remember(context) { context.canOpenGeoLinks() }
    return remember(viewModel, state, canOpenMaps) { LocationInputs({ state.value }, viewModel::onEvent, canOpenMaps) }
}

/**
 * Handles the "Location" sheet's [effects]: Android's request for the location, precise or approximate as the user
 * picks (both permissions together, as Android 12 and later require for the choice), whose answer goes to [onEvent]
 * with whether Android would ask again; the app's settings; the snackbar on [snackbarHostState] when the location is
 * off, whose action opens the location settings; and a maps app for the location found.
 */
@Composable
internal fun ShareLocationEffects(
    effects: Flow<ShareLocationEffect>,
    snackbarHostState: SnackbarHostState,
    onEvent: (ShareLocationEvent) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val canAskAgain =
                activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
            currentOnEvent(ShareLocationEvent.PermissionResult(canAskAgain))
        }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                ShareLocationEffect.RequestPermission -> {
                    permission.launch(LOCATION_PERMISSIONS)
                }

                ShareLocationEffect.OpenAppSettings -> {
                    context.startActivityIfAny(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                    )
                }

                ShareLocationEffect.LocationOff -> {
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.location_off),
                                actionLabel = resources.getString(R.string.location_off_settings),
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            context.startActivityIfAny(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    }
                }

                is ShareLocationEffect.OpenInMaps -> {
                    context.startActivityIfAny(Intent(Intent.ACTION_VIEW, geoUri(effect.location).toUri()))
                }
            }
        }
    }
}

/** Both location permissions: Android 12 and later then let the user pick a precise or an approximate location. */
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** The `geo:` link of [location], without a name. */
internal fun geoUri(location: DeviceLocation): String =
    geoUri(ChatEffect.OpenLocation(location.location.latitude, location.location.longitude, label = null))

/**
 * Whether an app on the device shows `geo:` links; the app's manifest declares the query, without which Android 11
 * and later hide every app from this.
 */
private fun Context.canOpenGeoLinks(): Boolean =
    Intent(Intent.ACTION_VIEW, "geo:0,0?q=0,0".toUri()).resolveActivity(packageManager) != null

/** Starts the settings page or the maps app of [intent]; nothing where the device has none. */
private fun Context.startActivityIfAny(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A device without that page or app: there is nothing else to open.
    }
}
