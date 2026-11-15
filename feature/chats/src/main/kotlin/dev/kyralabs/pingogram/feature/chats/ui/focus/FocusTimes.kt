package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Formats a moment (Unix seconds) as the switcher and the pill show it: the time in the language's short form, in the
 * phone's time zone ("6:00 PM", "18:00").
 */
@Composable
internal fun rememberFocusTime(): (Long) -> String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val format: (Long) -> String = { seconds ->
            Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).format(formatter)
        }
        format
    }
}
