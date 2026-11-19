package dev.kyralabs.pingogram.feature.chats.ui.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5), pushed above the Chats tab from its Feed tab: hoists its
 * [FeedViewModel]; a card opens its channel at the post through [onOpenPost], a post's "Forward" opens the forward
 * sheet through [onForward], and [onBack] returns to the chats. A copied link goes on the clipboard (Android 13 and
 * later confirm it themselves; before, a snackbar does), and bookmarks and failures show as snackbars. TDLib keeps the
 * posts current, so coming back from a channel reads nothing again.
 */
@Composable
internal fun FeedRoute(
    onBack: () -> Unit,
    onOpenPost: (chatId: Long, messageId: Long) -> Unit,
    onForward: (chatId: Long, messageIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    openPostKey: String? = null,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val uriHandler = LocalUriHandler.current
    val currentOnOpenPost by rememberUpdatedState(onOpenPost)
    val currentOnForward by rememberUpdatedState(onForward)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is FeedEffect.OpenPost -> {
                        currentOnOpenPost(effect.chatId, effect.messageId)
                        ""
                    }

                    is FeedEffect.Forward -> {
                        currentOnForward(effect.chatId, effect.messageIds)
                        ""
                    }

                    is FeedEffect.CopyLink -> {
                        context.copyLink(effect.link)
                        if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.TIRAMISU
                        ) {
                            ""
                        } else {
                            resources.getString(R.string.feed_link_copied)
                        }
                    }

                    FeedEffect.Bookmarked -> {
                        resources.getString(R.string.feed_bookmarked)
                    }

                    is FeedEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }

                    is FeedEffect.OpenUrl -> {
                        val opened = runCatching { uriHandler.openUri(effect.url) }.isSuccess
                        if (opened) "" else resources.getString(UiR.string.sponsored_no_app)
                    }

                    is FeedEffect.AdNotice -> {
                        resources.getString(effect.notice.text)
                    }
                }
            if (message.isNotEmpty()) snackbars.showSnackbar(message)
        }
    }
    FeedScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbars = snackbars,
        openPostKey = openPostKey,
        isRefreshing = refreshing,
    )
}

/** What a report of a sponsored message came to, as the conversation says it. */
private val FeedAdNotice.text: Int
    get() =
        when (this) {
            FeedAdNotice.Reported -> UiR.string.sponsored_reported
            FeedAdNotice.AdsHidden -> UiR.string.sponsored_ads_hidden
            FeedAdNotice.PremiumRequired -> UiR.string.sponsored_premium_required
            FeedAdNotice.ReportFailed -> UiR.string.sponsored_report_failed
        }

/** Puts [link] on the clipboard as plain text, labelled for the system's clipboard preview. */
private fun Context.copyLink(link: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.feed_link_label), link))
}
