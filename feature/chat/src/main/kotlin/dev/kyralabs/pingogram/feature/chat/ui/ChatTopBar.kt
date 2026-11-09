package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.AvatarTransformKey
import dev.kyralabs.pingogram.core.ui.transition.avatarTransform
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructShortText
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructText
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The conversation's top bar (`docs/DESIGN_SYSTEM.md` §13.5): Material's [CenterAlignedTopAppBar] on the screen's
 * `surfaceContainer`, above the sheet the messages scroll in, with back and the centred [ChatTitle], which opens the
 * other user's profile through [onOpenProfile] (`REQUIREMENTS.md` FR-P2) or a group's or channel's info page through
 * [onOpenChatInfo] (FR-P3). While TDLib is not connected ([connection]), "Waiting for network", "Connecting…" or
 * "Updating…" takes the status's place, as Telegram's apps do.
 *
 * The actions: in a group or channel [GroupActions] (a group's search, a channel's notifications toggle in its place
 * through [onSetMuted], then "More options"); elsewhere [ChatActions]: search ([onSearch], `REQUIREMENTS.md` §5.9),
 * then a private chat's call button ([ChatHeader.callUserId], through [LocalCallStarter]) or a secret chat's
 * self-destruct timer through [onSecretChatEvent] ([SecretChatActions], FR-E1), whose sheet opens its encryption key.
 * A forum's topic list and a topic's conversation add their topic action ([topicAction]: "New topic", "Manage topic")
 * after search. Without [onBack] there is no back button: in the detail pane beside the chat list, which has nothing
 * to go back to. It grows with the font scale instead of clipping its title ([titleHeight]).
 *
 * In selection mode ([selectedCount] not null, §6 "Selection mode") a [TopAppBar] of the same height shows
 * "3 selected" in `titleLarge` with a close button that leaves it ([onClearSelection]) in place of back; screen readers
 * hear the count change.
 */
@Composable
internal fun ChatTopBar(
    header: ChatHeader?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedCount: Int? = null,
    onClearSelection: () -> Unit = {},
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onSecretChatEvent: (SecretChatEvent) -> Unit = {},
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    topicAction: (@Composable () -> Unit)? = null,
    connection: ConnectionStatus = ConnectionStatus.Ready,
    onSetMuted: (Boolean) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val barColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = colors.surfaceContainer,
            scrolledContainerColor = colors.surfaceContainer,
        )
    if (selectedCount == null && header != null) {
        CenterAlignedTopAppBar(
            title = { ChatTitle(header, connection.labelRes(), onOpenProfile, onOpenChatInfo) },
            modifier = modifier,
            navigationIcon = { ChatNavigationIcon(selectedCount = null, onClearSelection = {}, onBack = onBack) },
            actions = {
                val openInfo = onOpenChatInfo.takeIf { header.opensChatInfo }
                if (header.opensChatInfo && topicAction == null) {
                    GroupActions(header, onSearch, openInfo?.let { { it(header.chatId) } }, onSetMuted)
                } else {
                    ChatActions(header, onSearch, onSecretChatEvent, topicAction)
                }
            },
            expandedHeight = titleHeight(),
            colors = barColors,
        )
        return
    }
    TopAppBar(
        title = { selectedCount?.let { SelectionTitle(it) } },
        modifier = modifier,
        navigationIcon = { ChatNavigationIcon(selectedCount, onClearSelection, onBack) },
        // The same height in selection mode, so entering it moves nothing.
        expandedHeight = titleHeight(),
        colors = barColors,
    )
}

/** "3 selected", a heading whose changes screen readers announce. */
@Composable
private fun SelectionTitle(count: Int) {
    Text(
        text = LocalResources.current.getQuantityString(R.plurals.selection_count, count, count),
        modifier =
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        maxLines = 1,
        style = MaterialTheme.typography.titleLarge,
    )
}

/** What the subtitle says while not connected, as the chat list's pill does; null once connected. */
internal fun ConnectionStatus.labelRes(): Int? =
    when (this) {
        ConnectionStatus.WaitingForNetwork -> R.string.chat_waiting_for_network
        ConnectionStatus.ConnectingToProxy -> R.string.chat_connecting_to_proxy
        ConnectionStatus.Connecting -> R.string.chat_connecting
        ConnectionStatus.Updating -> R.string.chat_updating
        ConnectionStatus.Ready -> null
    }
