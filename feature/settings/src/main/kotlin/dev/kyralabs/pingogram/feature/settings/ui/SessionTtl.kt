package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * "Automatically end inactive sessions": Telegram's choices as radio rows (a week, 1, 3 and 6 months, a year, and a
 * period another app set in its place), saved at once, the rows waiting while Telegram answers.
 */
@Composable
internal fun InactiveSessionsGroup(
    days: Int,
    saving: Boolean,
    onSelect: (Int) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_sessions_ttl_section),
        footer = stringResource(R.string.settings_sessions_ttl_footer),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        inactiveSessionTtlOptions(days).forEach { option ->
            item(key = TTL_KEY_PREFIX + option) { shapes ->
                RadioItem(
                    label = ttlText(option),
                    selected = option == days,
                    enabled = !saving,
                    shapes = shapes,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

/** Telegram's choices, with [current] among them where another app set a period of its own. */
internal fun inactiveSessionTtlOptions(current: Int): ImmutableList<Int> =
    if (current in InactiveSessionTtls || current <= 0) {
        InactiveSessionTtls
    } else {
        (InactiveSessionTtls + current).sorted().toImmutableList()
    }

/** The periods Telegram's apps offer, in days: a week, 1, 3 and 6 months and a year. */
internal val InactiveSessionTtls = persistentListOf(7, 30, 90, 180, 365)

private const val TTL_KEY_PREFIX = "ttl"
