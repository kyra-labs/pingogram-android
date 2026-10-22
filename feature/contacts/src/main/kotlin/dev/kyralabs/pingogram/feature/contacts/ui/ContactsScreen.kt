package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.ScrollToTopOnReselect
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.component.sidesOnly
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncControls
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Contacts tab (`REQUIREMENTS.md` FR-P1, `docs/DESIGN_SYSTEM.md` §2.2, §6), laid out like the Chats tab: a small
 * [TopAppBar] with "Contacts" in `titleLargeEmphasized` on `surfaceContainer`, and below it a rounded `surface` sheet
 * ([ContentSheet]) with
 * - the search bar's input field on its container ([ContactsSearchField]), which filters the list as the user types;
 * - one `LazyColumn`: "Add contact" ([onAddContact]) and "Invite friends" ([ContactsEvent.Invite]), then the contacts
 *   in alphabetical sections under sticky letter headers ([ContactRow]: a tap opens the chat, a long press offers Edit,
 *   [onEditContact], and Delete); while searching, the contacts that match instead, without sections;
 * - contacts sync ([ContactSyncItem]) below the actions: its offer or its paused state as a card, or its status row
 *   with "Sync now" ([onSyncEvent], [syncControls]); the contacts it found on the phone marked as such, and the phone's
 *   contacts not on Telegram last, under "Invite to Telegram" ([InviteRow]);
 * - the fast-scroll index of the letters along the end edge for a long list ([FastScrollIndex]).
 *
 * While the contacts load, the sheet shows the delayed loading indicator; without contacts, an empty state with
 * "Add contact"; after a failure, its error with "Try again". Deleting asks first ([DeleteContactDialog]). Like every
 * top-level screen it draws behind the floating navigation bar and keeps its content clear of it
 * ([screenContentWindowInsets]); tapping the tab again scrolls the list back to the top. On wide windows the list stays
 * one column, at most `contentMaxWidth` wide and centered.
 */
@Composable
internal fun ContactsScreen(
    state: ContactsUiState,
    onEvent: (ContactsEvent) -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    syncControls: ContactSyncControls = ContactSyncControls(),
    onSyncEvent: (ContactSyncEvent) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    onOpenMyQrCode: () -> Unit = {},
    onScanQrCode: () -> Unit = {},
) {
    ScrollToTopOnReselect(ContactsRoute, listState)
    val callbacks =
        remember(onEvent, onAddContact, onEditContact, onSyncEvent) {
            ContactsCallbacks(onEvent, onAddContact, onEditContact, onSyncEvent)
        }
    Scaffold(
        modifier = modifier,
        // The top bar's container; the content sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { ContactsTopBar(onOpenMyQrCode, onScanQrCode) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = screenContentWindowInsets(),
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            ContactsBody(state, syncControls, callbacks, listState, contentPadding)
        }
    }
    state.deleteDialog?.let { dialog ->
        DeleteContactDialog(
            name = dialog.name,
            onConfirm = { onEvent(ContactsEvent.ConfirmDelete) },
            onDismiss = { onEvent(ContactsEvent.DismissDialog) },
        )
    }
}

/**
 * The small top bar, its title at the window margin (`docs/DESIGN_SYSTEM.md` §7), as the Chats tab's, with two
 * `FilledTonalIconButton` actions, each with its tooltip (`REQUIREMENTS.md` FR-P5): the user's own QR code
 * ([onOpenMyQrCode]) and the QR code scanner ([onScanQrCode]).
 */
@Composable
private fun ContactsTopBar(
    onOpenMyQrCode: () -> Unit,
    onScanQrCode: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.contacts_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        actions = {
            TopBarAction(UiR.drawable.ic_qr_code, stringResource(R.string.contacts_my_qr_code), onOpenMyQrCode)
            TopBarAction(UiR.drawable.ic_qr_code_scanner, stringResource(R.string.contacts_scan_qr_code), onScanQrCode)
        },
        // The bar insets its title by the compact margin itself.
        modifier = Modifier.padding(horizontal = windowMargin() - MaterialTheme.spacing.windowMarginCompact),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        // A dense list's bar (`docs/DESIGN_SYSTEM.md` §7), as the Chats tab's.
        expandedHeight = MaterialTheme.sizes.denseTopAppBar,
    )
}

/** An action of the top bar: a `FilledTonalIconButton` with [icon], named [label], with a plain tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/**
 * What the sheet shows for [state]: the loading indicator, the empty state, the error, or the search field above the
 * list. Its own composable, so the frames in which the `Scaffold` measures its content anew stop here.
 */
@Composable
private fun ContactsBody(
    state: ContactsUiState,
    syncControls: ContactSyncControls,
    callbacks: ContactsCallbacks,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val onEvent = callbacks.onEvent
    val padded = Modifier.padding(contentPadding)
    when (val content = state.content) {
        ContactsContent.Loading -> {
            ContactsLoading(padded)
        }

        ContactsContent.Empty -> {
            NoContacts(callbacks.onAddContact, padded)
        }

        is ContactsContent.Error -> {
            ContactsError(content, onRetry = { onEvent(ContactsEvent.Retry) }, padded)
        }

        is ContactsContent.Contacts, is ContactsContent.Results -> {
            Column(Modifier.fillMaxSize()) {
                ContactsSearchField(
                    query = state.query,
                    onQueryChange = { onEvent(ContactsEvent.QueryChanged(it)) },
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(contentPadding.sidesOnly())
                            .padding(top = MaterialTheme.spacing.small)
                            .padding(horizontal = windowMargin())
                            .widthIn(max = MaterialTheme.sizes.contentMaxWidth),
                )
                Box(Modifier.weight(1f)) {
                    ContactsList(state, syncControls, callbacks, listState, contentPadding)
                }
            }
        }
    }
}

/**
 * The actions, then [content]'s contacts in one `LazyColumn`, every entry keyed and typed, at most `contentMaxWidth`
 * wide, animating on the motion scheme's springs as contacts come and go; the letters' headers stick to the top while
 * their section scrolls. Rows run no effects (`docs/PERFORMANCE.md` §4). With [ContactsContent.Contacts.showsIndex],
 * the [FastScrollIndex] lies over the list's end edge.
 */
@Composable
private fun ContactsList(
    state: ContactsUiState,
    syncControls: ContactSyncControls,
    callbacks: ContactsCallbacks,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val content = state.content
    val onEvent = callbacks.onEvent
    val onEditContact = callbacks.onEditContact
    val showsSync = state.sync != ContactSyncUi.Hidden
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val motionScheme = MaterialTheme.motionScheme
    val listPadding = remember(contentPadding) { contentPadding.withTop(0.dp) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(CONTACTS_LIST_TEST_TAG),
        state = listState,
        contentPadding = listPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (content) {
            is ContactsContent.Contacts -> {
                actionItems(state.sync, syncControls, callbacks, maxWidth, motionScheme)
                content.sections.forEach { section ->
                    stickyHeader(key = "$HEADER:${section.letter}", contentType = HEADER) {
                        SectionHeader(section.letter, Modifier.widthIn(max = maxWidth).fillMaxWidth())
                    }
                    contactItems(section.contacts, onEvent, onEditContact, maxWidth, motionScheme)
                }
                inviteItems(content.invites, onEvent, maxWidth, motionScheme)
            }

            is ContactsContent.Results -> {
                if (content.contacts.isEmpty()) {
                    item(key = NO_RESULTS, contentType = NO_RESULTS) {
                        NoResults(Modifier.row(this, maxWidth, motionScheme))
                    }
                } else {
                    contactItems(content.contacts, onEvent, onEditContact, maxWidth, motionScheme)
                }
            }

            ContactsContent.Loading, ContactsContent.Empty, is ContactsContent.Error -> {
                // ContactsBody shows these instead of the list.
            }
        }
    }
    if (content is ContactsContent.Contacts && content.showsIndex) {
        val letters = remember(content.sections) { content.sections.map { it.letter }.toImmutableList() }
        val leading = ACTION_ROWS + if (showsSync) 1 else 0
        val headers = remember(content.sections, leading) { headerIndices(content.sections, leading) }
        Box(Modifier.fillMaxSize().padding(listPadding), contentAlignment = Alignment.CenterEnd) {
            FastScrollIndex(letters = letters, headerIndices = headers, listState = listState)
        }
    }
}

/** What the tab's parts call, remembered once, so the list's composables skip when only their state stays. */
@Stable
internal class ContactsCallbacks(
    val onEvent: (ContactsEvent) -> Unit,
    val onAddContact: () -> Unit,
    val onEditContact: (userId: Long) -> Unit,
    val onSyncEvent: (ContactSyncEvent) -> Unit,
)

/** The test tag of the contacts list, for UI and recomposition tests. */
internal const val CONTACTS_LIST_TEST_TAG = "contacts:list"

@PingogramPreviews
@Composable
private fun ContactsScreenPreview(
    @PreviewParameter(ContactsPreviewStates::class) state: ContactsUiState,
) {
    PingogramPreview { ContactsScreen(state = state, onEvent = {}, onAddContact = {}, onEditContact = {}) }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun ContactsScreenExpandedPreview() {
    PingogramPreview {
        ContactsScreen(state = ContactsPreviewData.content, onEvent = {}, onAddContact = {}, onEditContact = {})
    }
}
