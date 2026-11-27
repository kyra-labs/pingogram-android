package dev.kyralabs.pingogram.feature.chat.ui.listen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.ListenEvent
import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import kotlinx.collections.immutable.toImmutableList

/**
 * What Listen to posts does around the conversation (`REQUIREMENTS.md` FR-X26): reading stops when the screen stops
 * (another screen covers it, the app goes to the background, or the chat is left), as nothing plays in the
 * background; and [viewModel]'s notices show in [snackbarHostState], "No voice on this phone reads this language
 * offline" with "Voices", which opens Android's text-to-speech settings to download one.
 */
@Composable
internal fun ListenEffects(
    viewModel: ListenViewModel,
    snackbarHostState: SnackbarHostState,
) {
    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.onEvent(ListenEvent.Stop) }
    }
    val resources = LocalResources.current
    val context = LocalContext.current
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.notices.collect { notice ->
            val voices = notice == ListenNotice.NoOfflineVoice
            val result =
                snackbarHostState.showSnackbar(
                    message = resources.getString(notice.message),
                    actionLabel = if (voices) resources.getString(R.string.listen_voices) else null,
                )
            if (result == SnackbarResult.ActionPerformed) context.openSpeechSettings()
        }
    }
}

@get:StringRes
private val ListenNotice.message: Int
    get() =
        when (this) {
            ListenNotice.NoOfflineVoice -> R.string.listen_no_offline_voice
            ListenNotice.Unavailable -> R.string.listen_unavailable
            ListenNotice.NothingToRead -> R.string.listen_nothing
            ListenNotice.NotLoaded -> R.string.listen_not_loaded
        }

/** Android's text-to-speech settings, where voices are downloaded; nothing where the phone has no such page. */
private fun Context.openSpeechSettings() {
    try {
        startActivity(Intent(TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some phones leave the page out; the snackbar has said what is missing.
    }
}

/** [this] with "Listen" taken out of its message's actions while Listen to messages is off ([enabled] false). */
internal fun ChatUiState.withListen(enabled: Boolean): ChatUiState {
    val sheet = messageActions
    return if (enabled || sheet == null || MessageAction.Listen !in sheet.actions) {
        this
    } else {
        val actions = sheet.actions.filterNot { it == MessageAction.Listen }
        copy(messageActions = sheet.copy(actions = actions.toImmutableList()))
    }
}

/**
 * The conversation's events with those of Listen to posts ([ListenEvent]) handed to [this] instead, [onListen] first
 * (the route puts the message's actions away), and the others to the next handler.
 */
internal fun ListenViewModel.routing(onListen: () -> Unit): ((ChatEvent) -> Unit) -> (ChatEvent) -> Unit =
    { next ->
        { event ->
            if (event is ListenEvent) {
                onListen()
                onEvent(event)
            } else {
                next(event)
            }
        }
    }

/** What "Listen to unread posts" does in [viewModel]'s channel, while it offers them; null otherwise. */
@Composable
internal fun rememberListenToUnread(viewModel: ListenViewModel): (() -> Unit)? {
    val offered by viewModel.canListenToUnread.collectAsStateWithLifecycle()
    return remember(viewModel, offered) { if (offered) ({ viewModel.onEvent(ListenEvent.Unread) }) else null }
}

/** "Listen to unread posts" in a channel's "More options", provided by its route; null offers none. */
internal val LocalListenToUnread = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Android's own action for its text-to-speech settings page (`Settings` names no constant for it). */
private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
