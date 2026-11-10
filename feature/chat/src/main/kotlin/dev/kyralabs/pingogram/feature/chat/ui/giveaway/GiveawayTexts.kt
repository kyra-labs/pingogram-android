package dev.kyralabs.pingogram.feature.chat.ui.giveaway

import android.content.res.Resources
import android.icu.text.ListFormatter
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// How giveaways are worded, for the bubbles, the details sheet and screen readers alike.

/** [epochSeconds] (Unix time) as a date and time in [locale]'s medium date and short time, in [zone]. */
internal fun giveawayDateText(
    epochSeconds: Long,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

/** The names of the countries of ISO 3166-1 alpha-2 [codes], as one list in [locale] ("Germany, India and Peru"). */
internal fun countriesText(
    codes: List<String>,
    locale: Locale,
): String = ListFormatter.getInstance(locale).format(codes.map { Locale("", it).getDisplayCountry(locale) })

/** [count] with the digits of [locale]. */
internal fun giveawayNumber(
    count: Long,
    locale: Locale,
): String = NumberFormat.getIntegerInstance(locale).format(count)

/** A count of Telegram Stars as a plural's quantity, which the plural rules take as an Int. */
internal fun Long.toPluralCount(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

/** The prize as a phrase: "Telegram Premium for 3 months", "500 Telegram Stars". */
internal fun Resources.prizeText(
    prize: GiveawayPrize,
    locale: Locale,
): String =
    when (prize) {
        is GiveawayPrize.Premium -> {
            getString(
                R.string.giveaway_premium_for,
                getQuantityString(R.plurals.giveaway_premium_months, prize.monthCount, prize.monthCount),
            )
        }

        is GiveawayPrize.Stars -> {
            getQuantityString(
                R.plurals.giveaway_stars_amount,
                prize.starCount.toPluralCount(),
                giveawayNumber(prize.starCount, locale),
            )
        }
    }

/** "3 people won Telegram Premium for 3 months". */
internal fun Resources.winnersWonText(
    winnerCount: Int,
    prize: GiveawayPrize,
    locale: Locale,
): String = getQuantityString(R.plurals.giveaway_winners_won, winnerCount, winnerCount, prizeText(prize, locale))
