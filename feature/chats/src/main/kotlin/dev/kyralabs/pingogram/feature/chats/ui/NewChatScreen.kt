package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.containerTransform
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.NewChatPreviewStates
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The New chat screen (`REQUIREMENTS.md` FR-L7, `docs/DESIGN_SYSTEM.md` §6), pushed above the Chats tab, as Google
 * Messages' Start chat: a small [TopAppBar] with back ([onBack]) and "New chat" on `surfaceContainer`, and below it on
 * a rounded `surface` sheet ([ContentSheet]) one list: New group, New channel and New contact ([NewChatAction]), then
 * the contacts under their header, each row opening its private chat ([NewChatEvent.OpenChat]). While the contacts
 * load the list shows the delayed loading indicator in their place; without any, a line that says so; after a failure,
 * its error with "Try again".
 *
 * The whole screen is the counterpart of the "New chat" button's container transform ([NewChatButton]): it grows out
 * of the button and shrinks back into it, its corners morphing between the button's and its own square ones.
 */
@Composable
internal fun NewChatScreen(
    state: NewChatUiState,
    onEvent: (NewChatEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier.containerTransform(
                NewChatTransformKey,
                shape = FullBleedShape,
                counterpartShape = MaterialTheme.shapes.large,
                // Laid out once and scaled as it grows: measuring the list anew in every frame made it stutter.
                fixedLayout = true,
            ),
        // The top bar's container; the list sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { NewChatTopBar(onBack) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            NewChatList(state, onEvent, Modifier.fillMaxSize(), contentPadding)
        }
    }
}

/** The small top bar: back and "New chat" in `titleLargeEmphasized`, on the screen's `surfaceContainer`. */
@Composable
private fun NewChatTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.new_chat),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.new_chat_back),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/**
 * The actions, then the contacts of [state], in one `LazyColumn`, every entry keyed and typed, at most
 * [contentMaxWidth][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes.contentMaxWidth] wide, animating in and out on
 * the motion scheme's springs as the contacts arrive. Rows run no effects (`docs/PERFORMANCE.md` §4).
 */
@Composable
private fun NewChatList(
    state: NewChatUiState,
    onEvent: (NewChatEvent) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val motionScheme = MaterialTheme.motionScheme
    val surface = MaterialTheme.colorScheme.surface
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(NewChatAction.entries, key = { it.name }, contentType = { ACTION_ROW }) { action ->
            NewChatActionRow(
                action = action,
                onClick = { onEvent(NewChatEvent.Action(action)) },
                modifier = Modifier.row(this, maxWidth, motionScheme),
            )
        }
        item(key = CONTACTS_HEADER, contentType = HEADER) {
            SectionHeader(stringResource(R.string.new_chat_contacts), Modifier.row(this, maxWidth, motionScheme))
        }
        when (state) {
            NewChatUiState.Loading -> {
                item(
                    key = LOADING,
                    contentType = LOADING,
                ) { ContactsLoading(Modifier.row(this, maxWidth, motionScheme)) }
            }

            NewChatUiState.Empty -> {
                item(key = EMPTY, contentType = EMPTY) { NoContacts(Modifier.row(this, maxWidth, motionScheme)) }
            }

            is NewChatUiState.Error -> {
                item(key = ERROR, contentType = ERROR) {
                    SectionError(
                        error = state.error,
                        onRetry = { onEvent(NewChatEvent.Retry) },
                        modifier = Modifier.row(this, maxWidth, motionScheme),
                    )
                }
            }

            is NewChatUiState.Content -> {
                items(state.contacts, key = { it.id }, contentType = { CONTACT_ROW }) { contact ->
                    SearchChatRow(
                        item = contact,
                        onClick = { onEvent(NewChatEvent.OpenChat(contact.id)) },
                        modifier = Modifier.row(this, maxWidth, motionScheme),
                        containerColor = surface,
                    )
                }
            }
        }
    }
}

/**
 * An action above the contacts: its Material Symbol in a `primaryContainer` circle the size of the contacts' avatars,
 * so the rows line up, and its label in the list item's `bodyLarge`.
 */
@Composable
private fun NewChatActionRow(
    action: NewChatAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ListItemDefaults.colors()
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(MaterialTheme.sizes.avatarSmall)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(action.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        colors = colors.copy(containerColor = Color.Transparent, contentColor = colors.contentColor),
        content = { Text(stringResource(action.label)) },
    )
}

/** The contacts loading: the loading indicator after a moment, named for screen readers. */
@Composable
private fun ContactsLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.new_chat_loading)
    Box(
        modifier = modifier.padding(vertical = MaterialTheme.spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** No contacts: a line that says so, in line with the header. */
@Composable
private fun NoContacts(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.new_chat_no_contacts),
        modifier =
            modifier.padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A row of [scope] at most [maxWidth] wide, animating as the list changes on the [motionScheme]'s springs. */
private fun Modifier.row(
    scope: LazyItemScope,
    maxWidth: Dp,
    motionScheme: MotionScheme,
): Modifier =
    with(scope) {
        this@row
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = motionScheme.defaultEffectsSpec(),
                placementSpec = motionScheme.defaultSpatialSpec(),
                fadeOutSpec = motionScheme.fastEffectsSpec(),
            )
    }

// Keys and content types of the list's entries; a contact's key is its chat's id.
private const val ACTION_ROW = "action"
private const val HEADER = "header"
private const val CONTACTS_HEADER = "contacts:header"
private const val LOADING = "loading"
private const val EMPTY = "empty"
private const val ERROR = "error"
private const val CONTACT_ROW = "contact"

@PingogramPreviews
@Composable
private fun NewChatScreenPreview(
    @PreviewParameter(NewChatPreviewStates::class) state: NewChatUiState,
) {
    PingogramPreview { NewChatScreen(state = state, onEvent = {}, onBack = {}) }
}
