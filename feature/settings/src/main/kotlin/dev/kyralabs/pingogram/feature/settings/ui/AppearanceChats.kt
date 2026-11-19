package dev.kyralabs.pingogram.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.systemContrastOrNull
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The "Chat list" group, how the list looks: "Chat type icons", the symbols before a group's or channel's name
 * (`docs/DESIGN_SYSTEM.md` §13.3), and "Show stories", whether the story rings show above the chats (they load only
 * then). The folder tabs' settings are on Chat folders.
 */
@Composable
internal fun ChatListGroup(
    chatList: ChatListSettings,
    onEvent: (AppearanceEvent) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_chat_list)) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_chat_type_icons),
                summary = stringResource(R.string.settings_chat_type_icons_summary),
                checked = chatList.showChatTypeIcons,
                shapes = shapes,
                onCheckedChange = { onEvent(AppearanceEvent.SetShowChatTypeIcons(it)) },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_show_stories),
                summary = stringResource(R.string.settings_show_stories_summary),
                checked = chatList.showStories,
                shapes = shapes,
                onCheckedChange = { onEvent(AppearanceEvent.SetShowStories(it)) },
            )
        }
    }
}

/** The row that opens the Chat appearance page: its name over the text size, the corners and the wallpaper. */
@Composable
internal fun ChatAppearanceItem(
    chat: ChatAppearance,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val percent =
        remember(chat.textScale, locale) {
            NumberFormat.getPercentInstance(locale).format(chat.textScale.toDouble())
        }
    SettingsRow(
        title = stringResource(R.string.settings_chat_appearance),
        shapes = shapes,
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_chat_appearance_summary,
                    percent,
                    chat.bubbleCorners.label(),
                    chat.wallpaper.label(),
                ),
            )
        },
        onClick = onClick,
    )
}
