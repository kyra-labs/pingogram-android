package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroupScope
import dev.kyralabs.pingogram.core.ui.R as UiR

/** Privacy and security's "Locked chats" row (`docs/adr/0029-locked-chats.md`), opening their page. */
internal fun SettingsGroupScope.lockedChatsItem(onClick: () -> Unit) =
    item { shapes ->
        PageItem(
            title = stringResource(R.string.locked_chats_title),
            summary = stringResource(R.string.settings_locked_chats_summary),
            icon = UiR.drawable.ic_lock,
            shapes = shapes,
            onClick = onClick,
        )
    }
