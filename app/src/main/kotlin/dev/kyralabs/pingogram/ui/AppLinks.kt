package dev.kyralabs.pingogram.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.domain.model.LinkSubject
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.component.floatingToolbarInsets
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.navigation.ShareNotices
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.link.LinkDispatcher
import dev.kyralabs.pingogram.link.LinkEffect
import dev.kyralabs.pingogram.link.LinkNotice
import dev.kyralabs.pingogram.link.LinkUriHandler
import dev.kyralabs.pingogram.link.openInBrowser
import dev.kyralabs.pingogram.navigation.OpenLinkEffect
import dev.kyralabs.pingogram.navigation.OpenShareEffect
import dev.kyralabs.pingogram.navigation.isInMainGraph
import dev.kyralabs.pingogram.navigation.openLinkTarget
import dev.kyralabs.pingogram.navigation.openShare
import dev.kyralabs.pingogram.share.IncomingShare
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's [UriHandler] (`LocalUriHandler`, provided by `MainActivity` around the app): Telegram links tapped
 * anywhere in the app open through [links] while someone is logged in ([LinkUriHandler]); signed out, or without
 * [links], they open in a browser. Every other link goes to [platform], the system's handler.
 */
@Composable
internal fun rememberLinkUriHandler(
    links: LinkDispatcher?,
    uiState: MainActivityUiState,
    platform: UriHandler = LocalUriHandler.current,
): UriHandler {
    val context = LocalContext.current
    val currentUiState by rememberUpdatedState(uiState)
    return remember(links, platform, context) {
        LinkUriHandler(platform) { link ->
            if (links != null && currentUiState == MainActivityUiState.SignedIn) {
                links.open(link)
            } else {
                // As the platform's handler does when no app opens the link.
                require(context.openInBrowser(link)) { "No app opens the link" }
            }
        }
    }
}

/**
 * Carries out what comes of opening links ([LinkDispatcher.effects], `docs/adr/0013-deep-links.md`): the screen of a
 * target ([openLinkTarget]; search through [onOpenSearch], a message to share through [onShare]), or a snackbar in
 * [snackbars] saying why the link does not open, with "Open in browser" for a web link Pingogram does not open. While
 * a link is looked up for longer than [LoadingIndicatorDelay], a snackbar says so, with "Cancel"; it goes as soon as
 * the link resolves. Nothing opens outside the signed-in app.
 */
@Composable
internal fun LinkEffects(
    links: LinkDispatcher,
    navController: NavHostController,
    snackbars: SnackbarHostState,
    onOpenSearch: () -> Unit,
    onShare: (SharedContent) -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnOpenSearch by rememberUpdatedState(onOpenSearch)
    val currentOnShare by rememberUpdatedState(onShare)
    LaunchedEffect(links, navController, snackbars) {
        links.effects.collect { effect ->
            when (effect) {
                is LinkEffect.Open -> {
                    val opened =
                        navController.currentDestination.isInMainGraph() &&
                            navController.openLinkTarget(
                                target = effect.target,
                                fromOutside = effect.fromOutside,
                                onOpenSearch = currentOnOpenSearch,
                                onShare = currentOnShare,
                            )
                    if (!opened) launch { snackbars.showSnackbar(resources.getString(R.string.link_not_yet)) }
                }

                is LinkEffect.Notice -> {
                    launch {
                        val message = noticeText(resources, effect.notice, showDetails)
                        if (message.isEmpty()) return@launch
                        val browserUrl = (effect.notice as? LinkNotice.Unsupported)?.browserUrl
                        val result =
                            snackbars.showSnackbar(
                                message = message,
                                actionLabel = browserUrl?.let { resources.getString(R.string.link_open_in_browser) },
                                withDismissAction = browserUrl != null,
                                duration = if (browserUrl != null) SnackbarDuration.Long else SnackbarDuration.Short,
                            )
                        if (result == SnackbarResult.ActionPerformed && browserUrl != null) {
                            if (!context.openInBrowser(browserUrl)) {
                                snackbars.showSnackbar(resources.getString(R.string.link_no_browser))
                            }
                        }
                    }
                }
            }
        }
    }
    val resolving by links.isResolving.collectAsStateWithLifecycle()
    LaunchedEffect(resolving, snackbars) {
        if (!resolving) return@LaunchedEffect
        delay(LoadingIndicatorDelay)
        val result =
            snackbars.showSnackbar(
                message = resources.getString(R.string.link_opening),
                actionLabel = resources.getString(R.string.link_opening_cancel),
                duration = SnackbarDuration.Indefinite,
            )
        if (result == SnackbarResult.ActionPerformed) links.cancel()
    }
}

/** The snackbar's text for [notice]; empty for an error that shows nothing (`DomainError.Silent`). */
internal fun noticeText(
    resources: Resources,
    notice: LinkNotice,
    showDetails: Boolean,
): String =
    when (notice) {
        is LinkNotice.Unsupported -> {
            resources.getString(R.string.link_not_supported)
        }

        LinkNotice.NotYet -> {
            resources.getString(R.string.link_not_yet)
        }

        LinkNotice.LoginCode -> {
            resources.getString(R.string.link_login_code)
        }

        is LinkNotice.NotFound -> {
            resources.getString(
                when (notice.subject) {
                    LinkSubject.Username -> R.string.link_not_found_username
                    LinkSubject.PhoneNumber -> R.string.link_not_found_phone_number
                    LinkSubject.UserToken -> R.string.link_not_found_user_token
                    LinkSubject.Message -> R.string.link_not_found_message
                },
            )
        }

        is LinkNotice.Failed -> {
            errorMessage(resources, notice.error, showDetails)
        }
    }

/**
 * Telegram links and shares in the shell (`docs/adr/0013-deep-links.md`, `REQUIREMENTS.md` FR-N2), through [links] and
 * [shares]: [linkToOpen], the link another app opened Pingogram with, goes to [links] once the account is logged in
 * ([OpenLinkEffect]) and is reported to [onLinkOpened]; what comes of every link ([LinkEffects], search through
 * [onOpenSearch]) opens a screen or shows a snackbar. [shareToOpen], what another app shared, opens the share sheet or
 * a Direct Share target's chat once logged in ([OpenShareEffect], [openShare]), and is reported to [onShareOpened];
 * a `t.me/share` link opens the share sheet too. Snackbars, those of shares sent to several chats included
 * ([ShareNotices]), show at the bottom of the window above the floating navigation bar's place and the keyboard.
 */
@Suppress("LongParameterList") // What the shell opens (links, shares), when, and what it reports.
@Composable
internal fun BoxScope.LinkHandling(
    links: LinkDispatcher?,
    uiState: MainActivityUiState,
    navController: NavHostController,
    onOpenSearch: () -> Unit,
    linkToOpen: String? = null,
    onLinkOpened: () -> Unit = {},
    shares: ShareInbox? = null,
    shareToOpen: IncomingShare? = null,
    onShareOpened: () -> Unit = {},
) {
    val snackbars = remember { SnackbarHostState() }
    val share =
        remember(shares, navController) {
            { content: SharedContent ->
                shares?.let { navController.openShare(content, chatId = null, inbox = it, fromOutside = false) }
                Unit
            }
        }
    if (links != null) {
        val openFromOutside = { link: String -> links.open(link, fromOutside = true) }
        OpenLinkEffect(navController, uiState, linkToOpen, onOpen = openFromOutside, onHandled = onLinkOpened)
        LinkEffects(links, navController, snackbars, onOpenSearch, onShare = share)
    }
    if (shares != null) {
        val openFromOutside = { incoming: IncomingShare ->
            navController.openShare(incoming.content, incoming.chatId, shares, fromOutside = true)
        }
        OpenShareEffect(navController, uiState, shareToOpen, onOpen = openFromOutside, onHandled = onShareOpened)
        ShareNotices(snackbars)
    }
    SnackbarHost(
        hostState = snackbars,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(floatingToolbarInsets())),
    )
}
