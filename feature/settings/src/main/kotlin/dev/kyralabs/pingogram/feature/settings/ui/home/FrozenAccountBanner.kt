package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.AccountFreeze
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsBannerCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The frozen account's notice above Settings home's account card (`docs/DESIGN_SYSTEM.md` §6 "Frozen account"):
 * [SettingsBannerCard] on `errorContainer`, the lock on its badge, "Your account is frozen" over what the account can
 * still do and when Telegram deletes it, and "Appeal", which opens Telegram's appeal link. A polite live region, so
 * screen readers hear it as it comes.
 */
@Composable
internal fun FrozenAccountBanner(
    freeze: AccountFreeze,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val locale: Locale = LocalConfiguration.current.locales[0]
    val deletion =
        remember(freeze.deletesAt, locale) {
            freeze.deletesAt.takeIf { it > 0 }?.let { seconds ->
                DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.LONG)
                    .withLocale(locale)
                    .format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()))
            }
        }
    SettingsBannerCard(
        icon = UiR.drawable.ic_lock,
        title = stringResource(R.string.settings_frozen_title),
        summary =
            deletion?.let { stringResource(R.string.settings_frozen_text, it) }
                ?: stringResource(R.string.settings_frozen_text_no_date),
        button = stringResource(R.string.settings_frozen_appeal),
        onClick = { freeze.appealLink.takeIf { it.isNotBlank() }?.let { runCatching { uriHandler.openUri(it) } } },
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@PingogramPreviews
@Composable
private fun FrozenAccountBannerPreview() {
    PingogramPreview {
        FrozenAccountBanner(
            AccountFreeze(frozenAt = 1_798_000_000, deletesAt = 1_800_532_800, appealLink = "https://t.me/spambot"),
        )
    }
}
