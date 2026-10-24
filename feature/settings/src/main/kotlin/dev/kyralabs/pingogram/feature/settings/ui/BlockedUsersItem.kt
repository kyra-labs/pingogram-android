package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import java.text.NumberFormat

/** The blocked users' row, with how many there are. */
@Composable
internal fun BlockedUsersItem(
    count: Int,
    numbers: NumberFormat,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    PageItem(
        title = stringResource(R.string.settings_blocked_users),
        summary =
            if (count == 0) {
                stringResource(R.string.settings_blocked_users_none)
            } else {
                pluralStringResource(R.plurals.settings_blocked_users_count, count, numbers.format(count))
            },
        icon = R.drawable.ic_block,
        shapes = shapes,
        onClick = onClick,
    )
}
