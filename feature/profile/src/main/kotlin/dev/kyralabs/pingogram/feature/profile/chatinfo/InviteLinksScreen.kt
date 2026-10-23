package dev.kyralabs.pingogram.feature.profile.chatinfo

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.copyPlainText
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor
import dev.kyralabs.pingogram.feature.profile.ui.shareText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The invite links page of the route's group or channel: hoists its [InviteLinksViewModel] and handles its effects.
 * The editor opens through [onOpenEditor] (a new link with null); a copied link goes to the clipboard (a snackbar
 * before Android 13), a shared one to the share sheet; what was done and errors are snackbars. [refresh] is true when
 * the page shows again after the editor saved, and [onRefreshed] clears it.
 */
@Suppress("LongParameterList") // The navigation, and the platform actions tests replace.
@Composable
internal fun InviteLinksRoute(
    onBack: () -> Unit,
    onOpenEditor: (link: String?) -> Unit,
    modifier: Modifier = Modifier,
    refresh: Boolean = false,
    onRefreshed: () -> Unit = {},
    viewModel: InviteLinksViewModel = hiltViewModel(),
    copyText: Context.(String) -> Unit = Context::copyPlainText,
    share: Context.(String) -> Unit = Context::shareText,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnOpenEditor by rememberUpdatedState(onOpenEditor)
    LaunchedEffect(refresh) {
        if (refresh) {
            viewModel.onEvent(InviteLinksEvent.Refresh)
            onRefreshed()
        }
    }
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is InviteLinksEffect.OpenEditor -> {
                        currentOnOpenEditor(effect.link)
                        ""
                    }

                    is InviteLinksEffect.Copy -> {
                        context.copyText(effect.link)
                        if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.TIRAMISU
                        ) {
                            ""
                        } else {
                            resources.getString(R.string.chat_info_link_copied)
                        }
                    }

                    is InviteLinksEffect.Share -> {
                        context.share(effect.link)
                        ""
                    }

                    is InviteLinksEffect.Done -> {
                        effect.text.resolve(resources)
                    }

                    is InviteLinksEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }
                }
            if (message.isNotEmpty()) {
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    InviteLinksScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The invite links page (`docs/DESIGN_SYSTEM.md` §6 "Invite links"), a settings-style page:
 * - **Primary link**: a segment with the link (left to right) and how many joined, then Copy, Share and QR code as
 *   `FilledTonalButton`s sharing the row; "Revoke link" in `error` below it, which replaces it.
 * - **Your links**: "Create a new link", then the other links (name, "3 joined · Expires Oct 30"); a tap opens a menu
 *   of Copy, Share, QR code, Edit and Revoke (in `error`, in a group of its own).
 * - **Revoked links**: each with a "Delete" `TextButton`, then "Delete all revoked links" in `error`.
 *
 * Revoking and deleting ask first with an `AlertDialog`; the QR code shows in a sheet ([QrCodeSheet]).
 */
@Composable
internal fun InviteLinksScreen(
    state: InviteLinksUiState,
    onEvent: (InviteLinksEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    InfoPageScaffold(
        title = stringResource(R.string.chat_info_invite_links),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        when (state) {
            InviteLinksUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is InviteLinksUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(InviteLinksEvent.Retry) } }
            }

            is InviteLinksUiState.Content -> {
                InfoPageContent(innerPadding) {
                    state.primary?.let { PrimaryLinkGroup(it, isBusy = it.link in state.busy, onEvent = onEvent) }
                    OtherLinksGroup(state, onEvent)
                    if (state.revoked.isNotEmpty()) RevokedLinksGroup(state, onEvent)
                }
            }
        }
    }
    (state as? InviteLinksUiState.Content)?.dialog?.let { InviteLinksDialogs(it, onEvent) }
}

@Composable
private fun PrimaryLinkGroup(
    link: InviteLinkItem,
    isBusy: Boolean,
    onEvent: (InviteLinksEvent) -> Unit,
) {
    val rows =
        listOf(
            ProfileRow("primary") { shapes ->
                Surface(shape = shapes.shape, color = profileRowContainerColor()) {
                    Column(Modifier.padding(bottom = MaterialTheme.spacing.medium)) {
                        SegmentedListItem(
                            shapes = shapes,
                            colors = profileRowColors(),
                            supportingContent = { Text(link.detail.resolve()) },
                        ) {
                            Text(
                                link.link.removePrefix("https://"),
                                style = MaterialTheme.typography.bodyLarge.numericContent,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        ) {
                            LinkButton(
                                R.string.chat_info_link_copy,
                                Modifier.weight(1f),
                            ) { onEvent(InviteLinksEvent.Copy(link.link)) }
                            LinkButton(
                                R.string.chat_info_link_share,
                                Modifier.weight(1f),
                            ) { onEvent(InviteLinksEvent.Share(link.link)) }
                            LinkButton(
                                R.string.chat_info_qr_code,
                                Modifier.weight(1f),
                            ) { onEvent(InviteLinksEvent.ShowQrCode(link.link)) }
                        }
                    }
                }
            },
            ProfileRow("revoke-primary") { shapes ->
                ChatInfoActionRow(
                    text = stringResource(R.string.chat_info_link_revoke),
                    shapes = shapes,
                    onClick = { onEvent(InviteLinksEvent.Revoke(link.link)) },
                    enabled = !isBusy,
                    contentColor = MaterialTheme.colorScheme.error,
                )
            },
        )
    ProfileGroup(rows, title = stringResource(R.string.chat_info_primary_link))
}

@Composable
private fun LinkButton(
    @StringRes label: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) {
        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OtherLinksGroup(
    state: InviteLinksUiState.Content,
    onEvent: (InviteLinksEvent) -> Unit,
) {
    val rows = mutableListOf<ProfileRow>()
    rows +=
        ProfileRow("create") { shapes ->
            ChatInfoActionRow(
                text = stringResource(R.string.chat_info_link_create),
                shapes = shapes,
                onClick = { onEvent(InviteLinksEvent.Create) },
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    state.links.forEach { link ->
        rows +=
            ProfileRow(
                link.link,
            ) { shapes -> LinkRow(link, shapes, isBusy = link.link in state.busy, onEvent = onEvent) }
    }
    ProfileGroup(rows, title = stringResource(R.string.chat_info_your_links))
}

/** A link of the user's: a tap opens its menu; screen readers get its actions as the menu's items. */
@Composable
private fun LinkRow(
    link: InviteLinkItem,
    shapes: ListItemShapes,
    isBusy: Boolean,
    onEvent: (InviteLinksEvent) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        SegmentedListItem(
            onClick = { menuOpen = true },
            shapes = shapes,
            enabled = !isBusy,
            colors = profileRowColors(),
            supportingContent = { Text(link.detail.resolve()) },
        ) {
            Text(
                link.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.userContent,
            )
        }
        DropdownMenuPopup(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val everyday =
                listOfNotNull(
                    R.string.chat_info_link_copy to InviteLinksEvent.Copy(link.link),
                    R.string.chat_info_link_share to InviteLinksEvent.Share(link.link),
                    (R.string.chat_info_qr_code to InviteLinksEvent.ShowQrCode(link.link)).takeIf { link.isActive },
                    (R.string.chat_info_link_edit to InviteLinksEvent.Edit(link.link)).takeIf { link.canEdit },
                )
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                everyday.forEachIndexed { index, (label, event) ->
                    DropdownMenuItem(
                        onClick = {
                            menuOpen = false
                            onEvent(event)
                        },
                        text = { Text(stringResource(label)) },
                        shape = MenuDefaults.itemShape(index, everyday.size).shape,
                    )
                }
            }
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        onEvent(InviteLinksEvent.Revoke(link.link))
                    },
                    text = { Text(stringResource(R.string.chat_info_link_revoke)) },
                    shape = MenuDefaults.itemShape(0, 1).shape,
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

@Composable
private fun RevokedLinksGroup(
    state: InviteLinksUiState.Content,
    onEvent: (InviteLinksEvent) -> Unit,
) {
    val rows = mutableListOf<ProfileRow>()
    state.revoked.forEach { link ->
        rows +=
            ProfileRow(link.link) { shapes ->
                val delete = stringResource(R.string.chat_info_link_delete_named, link.title)
                SegmentedListItem(
                    shapes = shapes,
                    colors = profileRowColors(),
                    supportingContent = { Text(link.detail.resolve()) },
                    trailingContent = {
                        TextButton(
                            onClick = { onEvent(InviteLinksEvent.Delete(link.link)) },
                            shapes = ButtonDefaults.shapes(),
                            enabled = link.link !in state.busy,
                            modifier = Modifier.semantics { contentDescription = delete },
                        ) {
                            Text(stringResource(R.string.chat_info_link_delete))
                        }
                    },
                ) {
                    Text(
                        link.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.userContent,
                    )
                }
            }
    }
    rows +=
        ProfileRow("delete-all") { shapes ->
            ChatInfoActionRow(
                text = stringResource(R.string.chat_info_links_delete_all),
                shapes = shapes,
                onClick = { onEvent(InviteLinksEvent.DeleteAllRevoked) },
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    ProfileGroup(rows, title = stringResource(R.string.chat_info_revoked_links))
}

@Composable
private fun InviteLinksDialogs(
    dialog: InviteLinksDialog,
    onEvent: (InviteLinksEvent) -> Unit,
) {
    val confirm = { onEvent(InviteLinksEvent.Confirm) }
    val dismiss = { onEvent(InviteLinksEvent.DismissDialog) }
    when (dialog) {
        is InviteLinksDialog.Revoke -> {
            ConfirmationDialog(
                title = stringResource(R.string.chat_info_link_revoke_title),
                text =
                    stringResource(
                        if (dialog.isPrimary) {
                            R.string.chat_info_link_revoke_primary_text
                        } else {
                            R.string.chat_info_link_revoke_text
                        },
                    ),
                confirm = stringResource(R.string.chat_info_link_revoke_confirm),
                onConfirm = confirm,
                onDismiss = dismiss,
            )
        }

        is InviteLinksDialog.Delete -> {
            ConfirmationDialog(
                title = stringResource(R.string.chat_info_link_delete_title),
                text = stringResource(R.string.chat_info_link_delete_text),
                confirm = stringResource(R.string.chat_info_link_delete),
                onConfirm = confirm,
                onDismiss = dismiss,
            )
        }

        InviteLinksDialog.DeleteAllRevoked -> {
            ConfirmationDialog(
                title = stringResource(R.string.chat_info_links_delete_all_title),
                text = stringResource(R.string.chat_info_link_delete_text),
                confirm = stringResource(R.string.chat_info_link_delete),
                onConfirm = confirm,
                onDismiss = dismiss,
            )
        }

        is InviteLinksDialog.QrCode -> {
            QrCodeSheet(dialog.link, onShare = { onEvent(InviteLinksEvent.Share(dialog.link)) }, onDismiss = dismiss)
        }
    }
}
