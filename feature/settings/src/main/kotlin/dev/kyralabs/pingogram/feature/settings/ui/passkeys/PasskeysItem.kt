package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroupScope
import dev.kyralabs.pingogram.core.ui.R as UiR

/** Privacy and security's "Passkeys" row, which opens the Passkeys page (`docs/adr/0037-passkeys.md`). */
internal fun SettingsGroupScope.passkeysItem(onClick: () -> Unit) =
    item { shapes ->
        PageItem(
            title = stringResource(R.string.settings_passkeys),
            summary = stringResource(R.string.settings_passkeys_summary),
            icon = UiR.drawable.ic_key,
            shapes = shapes,
            onClick = onClick,
        )
    }
