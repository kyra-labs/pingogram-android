package dev.kyralabs.pingogram.feature.chats.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The request to allow notifications above the chat list (Android 13+, [NotificationPromptViewModel]): it reads the
 * permission whenever the screen shows (the user may have changed it in the system settings), shows the system's
 * dialog or the app's notification settings when the card asks for them, and grows in and shrinks away on the motion
 * scheme's springs. Before Android 13 notifications need no permission, and nothing shows.
 */
@Composable
internal fun NotificationPromptHost(
    modifier: Modifier = Modifier,
    viewModel: NotificationPromptViewModel = hiltViewModel(),
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val check = rememberPermissionCheck(onChecked = viewModel::onEvent)
    LifecycleResumeEffect(check) {
        check()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { check() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                NotificationPromptEffect.RequestPermission -> {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                NotificationPromptEffect.OpenSettings -> {
                    val intent =
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // A system without notification settings for apps (none known): nothing to open.
                    }
                }
            }
        }
    }
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = state != NotificationPromptUiState.Hidden,
        modifier = modifier,
        enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(motionScheme.defaultSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        NotificationPromptCard(state = state, onEvent = viewModel::onEvent)
    }
}

/** Reads the notification permission of the activity and reports it to [onChecked]. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberPermissionCheck(onChecked: (NotificationPromptEvent) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val currentOnChecked by rememberUpdatedState(onChecked)
    return remember(activity) {
        {
            if (activity != null) {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                currentOnChecked(
                    NotificationPromptEvent.PermissionChecked(
                        granted =
                            ContextCompat.checkSelfPermission(activity, permission) ==
                                PackageManager.PERMISSION_GRANTED,
                        shouldShowRationale = activity.shouldShowRequestPermissionRationale(permission),
                    ),
                )
            }
        }
    }
}

/**
 * The card of [state]: a notifications icon, a title and why, then "Not now" and "Allow", or "Open settings" for
 * [NotificationPromptUiState.Blocked] ([NotificationPromptUiState.Hidden], while the card shrinks away, looks like
 * [NotificationPromptUiState.Ask]). A filled card on `surfaceContainerHigh` over the
 * list's `surface` sheet, inset like the rows and as wide as they are at most.
 */
@Composable
internal fun NotificationPromptCard(
    state: NotificationPromptUiState,
    onEvent: (NotificationPromptEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = state == NotificationPromptUiState.Blocked
    val spacing = MaterialTheme.spacing
    Card(
        modifier =
            modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(start = spacing.small, top = spacing.small, end = spacing.small),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        NotificationPromptText(blocked)
        NotificationPromptButtons(blocked, onEvent)
    }
}

/** The icon, title and explanation of the card. */
@Composable
private fun NotificationPromptText(blocked: Boolean) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.padding(start = spacing.large, top = spacing.large, end = spacing.large)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(UiR.drawable.ic_notifications),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            val title = if (blocked) R.string.notification_prompt_blocked_title else R.string.notification_prompt_title
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .padding(start = spacing.large)
                        .semantics { heading() },
            )
        }
        val body = if (blocked) R.string.notification_prompt_blocked_body else R.string.notification_prompt_body
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.small),
        )
    }
}

/** "Not now", then "Allow" or, [blocked], "Open settings", at the end of the card. */
@Composable
private fun NotificationPromptButtons(
    blocked: Boolean,
    onEvent: (NotificationPromptEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
    ) {
        TextButton(onClick = { onEvent(NotificationPromptEvent.NotNow) }, shapes = ButtonDefaults.shapes()) {
            Text(
                text = stringResource(R.string.notification_prompt_not_now),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
        Button(
            onClick = { onEvent(if (blocked) NotificationPromptEvent.OpenSettings else NotificationPromptEvent.Allow) },
            shapes = ButtonDefaults.shapes(),
        ) {
            val action = if (blocked) R.string.notification_prompt_open_settings else R.string.notification_prompt_allow
            Text(
                text = stringResource(action),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** The card's two states. */
internal class NotificationPromptStates : PreviewParameterProvider<NotificationPromptUiState> {
    override val values =
        sequenceOf(NotificationPromptUiState.Ask, NotificationPromptUiState.Blocked)
}

@PingogramPreviews
@Composable
private fun NotificationPromptCardPreview(
    @PreviewParameter(NotificationPromptStates::class) state: NotificationPromptUiState,
) {
    PingogramPreview { NotificationPromptCard(state = state, onEvent = {}) }
}
