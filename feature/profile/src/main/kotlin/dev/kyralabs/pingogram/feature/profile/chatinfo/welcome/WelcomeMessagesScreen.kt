package dev.kyralabs.pingogram.feature.profile.chatinfo.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.ItemMotion
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.ConfirmationDialog
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageBody
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageScaffold
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A group's welcome messages (`docs/DESIGN_SYSTEM.md` §6 "Welcome message setup", the owner's board 20): a
 * settings-style page ([InfoPageScaffold], "Welcome message") with the main switch on a `primaryContainer` card,
 * then, while it is on, "Messages" with how many of the limit over a card per message ([WelcomeMessageCard]), "Add
 * message", a footer, and what new members see ([WelcomePreview]). The editor is a sheet ([WelcomeEditorSheet]).
 * Loading is a delayed indicator, a failure an [EmptyState] on `Cookie4Sided` with "Try again".
 */
@Composable
internal fun WelcomeMessagesScreen(
    state: WelcomeMessagesUiState,
    onEvent: (WelcomeMessagesEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    InfoPageScaffold(
        title = stringResource(R.string.welcome_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        InfoPageBody(innerPadding) { contentPadding ->
            when (state) {
                WelcomeMessagesUiState.Loading -> {
                    val description = stringResource(R.string.welcome_loading)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
                    }
                }

                is WelcomeMessagesUiState.Error -> {
                    WelcomeError(state, onEvent)
                }

                is WelcomeMessagesUiState.Content -> {
                    WelcomeContent(state, onEvent, contentPadding)
                }
            }
        }
    }
    (state as? WelcomeMessagesUiState.Content)?.let { content ->
        WelcomeDialogs(content.dialog, onEvent)
        content.editor?.let { WelcomeEditorSheet(it, onEvent) }
    }
}

/** Why the welcome messages could not be read, with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeError(
    state: WelcomeMessagesUiState.Error,
    onEvent: (WelcomeMessagesEvent) -> Unit,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.welcome_error_title),
        body = errorText(state.error),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = { onEvent(WelcomeMessagesEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

/** The switch, then while it is on the messages, "Add message", the footer and the preview. */
@Composable
private fun WelcomeContent(
    state: WelcomeMessagesUiState.Content,
    onEvent: (WelcomeMessagesEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val spacing = MaterialTheme.spacing
    val rowModifier =
        Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = windowMargin())
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    // Every item moves on the scheme's springs, so the switch, the footer and the preview move with the cards.
    val itemMotion = rememberItemMotion()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = remember(contentPadding, spacing) { contentPadding.plusBottom(spacing.extraLarge) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = SWITCH, contentType = SWITCH) {
            WelcomeSwitchCard(
                state,
                onEvent,
                rowModifier.animateItem(this, itemMotion).padding(bottom = spacing.medium),
            )
        }
        if (state.isOn) {
            item(key = TITLE, contentType = TITLE) {
                MessagesHeader(
                    numbers.format(state.messages.size),
                    numbers.format(state.countMax),
                    rowModifier.animateItem(this, itemMotion),
                )
            }
            itemsIndexed(state.messages, key = { _, item -> item.id }, contentType = { _, _ -> CARD }) { index, item ->
                WelcomeMessageCard(
                    item = item,
                    position = index + 1,
                    shape = ListItemDefaults.segmentedShapes(index, state.messages.size).shape,
                    isDeleting = item.id in state.deleting,
                    onEvent = onEvent,
                    modifier = rowModifier.animateItem(this, itemMotion),
                )
            }
            item(key = ADD, contentType = ADD) {
                AddMessageButton(
                    enabled = state.canAdd && !state.isBusy,
                    onClick = { onEvent(WelcomeMessagesEvent.Add) },
                    modifier = rowModifier.animateItem(this, itemMotion).padding(top = spacing.small),
                )
            }
        }
        footerItem(state, numbers, rowModifier, itemMotion)
        if (state.isOn) {
            item(key = PREVIEW, contentType = PREVIEW) {
                WelcomePreview(state.messages, rowModifier.animateItem(this, itemMotion).padding(top = spacing.medium))
            }
        }
    }
}

/** Who sees the messages, or the limit once it is reached. */
private fun LazyListScope.footerItem(
    state: WelcomeMessagesUiState.Content,
    numbers: NumberFormat,
    rowModifier: Modifier,
    itemMotion: ItemMotion,
) {
    item(key = FOOTER, contentType = FOOTER) {
        val footer =
            if (state.isOn && !state.canAdd) {
                stringResource(R.string.welcome_limit, numbers.format(state.countMax))
            } else {
                stringResource(R.string.welcome_footer)
            }
        Text(
            text = footer,
            modifier =
                rowModifier
                    .animateItem(
                        this,
                        itemMotion,
                    ).padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * "Welcome new members" over "Shown once to each person who joins" on a `primaryContainer` card with `extraLarge`
 * corners, a [CheckSwitch] at its end; the whole card toggles it, one switch for screen readers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeSwitchCard(
    state: WelcomeMessagesUiState.Content,
    onEvent: (WelcomeMessagesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .toggleable(
                        value = state.isOn,
                        enabled = !state.isBusy,
                        role = Role.Switch,
                        onValueChange = { onEvent(WelcomeMessagesEvent.Toggle(it)) },
                    ).padding(horizontal = spacing.large, vertical = spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                Text(
                    text = stringResource(R.string.welcome_switch_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = stringResource(R.string.welcome_switch_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.isBusy) {
                LoadingIndicator(Modifier.size(MaterialTheme.sizes.leadingIconContainer))
            } else {
                CheckSwitch(checked = state.isOn)
            }
        }
    }
}

/** "Messages", a heading, with "2 of 5" at its end. */
@Composable
private fun MessagesHeader(
    count: String,
    max: String,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.welcome_count_description, count, max)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ProfileSectionHeader(
            title = stringResource(R.string.welcome_messages),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.welcome_count, count, max),
            modifier =
                Modifier
                    .padding(end = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.small)
                    .semantics { contentDescription = description },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** "Add message", an outlined button across the column at Material's medium height, the add symbol before it. */
@Composable
private fun AddMessageButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = modifier.heightIn(height),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
        Text(text = stringResource(R.string.welcome_add), style = ButtonDefaults.textStyleFor(height))
    }
}

/** The page's questions: deleting one message, or turning welcoming off, which deletes them all. */
@Composable
private fun WelcomeDialogs(
    dialog: WelcomeDialog?,
    onEvent: (WelcomeMessagesEvent) -> Unit,
) {
    when (dialog) {
        is WelcomeDialog.Delete -> {
            ConfirmationDialog(
                title = stringResource(R.string.welcome_delete_title, dialog.position),
                text = stringResource(R.string.welcome_delete_text),
                confirm = stringResource(R.string.welcome_delete_confirm),
                onConfirm = { onEvent(WelcomeMessagesEvent.ConfirmDialog) },
                onDismiss = { onEvent(WelcomeMessagesEvent.DismissDialog) },
            )
        }

        WelcomeDialog.TurnOff -> {
            ConfirmationDialog(
                title = stringResource(R.string.welcome_turn_off_title),
                text = stringResource(R.string.welcome_turn_off_text),
                confirm = stringResource(R.string.welcome_turn_off_confirm),
                onConfirm = { onEvent(WelcomeMessagesEvent.ConfirmDialog) },
                onDismiss = { onEvent(WelcomeMessagesEvent.DismissDialog) },
            )
        }

        null -> {}
    }
}

private const val SWITCH = "switch"
private const val TITLE = "title"
private const val CARD = "card"
private const val ADD = "add"
private const val FOOTER = "footer"
private const val PREVIEW = "preview"
