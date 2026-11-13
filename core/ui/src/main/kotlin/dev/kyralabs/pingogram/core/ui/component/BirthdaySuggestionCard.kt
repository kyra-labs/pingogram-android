package dev.kyralabs.pingogram.core.ui.component

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A birthday suggestion (td_api.tl `messageSuggestBirthdate`) as the conversation shows it, the owner's board 16b.
 *
 * @property birthdate the birthday suggested.
 * @property name the other person's first name: who suggested it, or for [outgoing] whom it was suggested to.
 * @property outgoing the current user suggested it: the card only says so, without buttons.
 * @property privacy who may see the birthday once kept, the Birthday privacy setting's choice ("My contacts"); null
 *   while it is not known.
 * @property status where the receiver's answer stands.
 */
@Immutable
data class BirthdaySuggestion(
    val birthdate: Birthdate,
    val name: String,
    val outgoing: Boolean = false,
    val privacy: String? = null,
    val status: BirthdaySuggestionStatus = BirthdaySuggestionStatus.Pending,
) {
    override fun toString() = "BirthdaySuggestion(outgoing=$outgoing, status=$status, …)"
}

/** Where the answer to a [BirthdaySuggestion] stands. */
enum class BirthdaySuggestionStatus {
    /** Not answered yet: "Decline" and "Accept". */
    Pending,

    /** "Accept" was tapped and Telegram has not answered yet (`setBirthdate`). */
    Accepting,

    /** The birthday is on the user's profile: "Added to your profile". */
    Accepted,

    /** The user declined it: "Declined". */
    Declined,
}

/**
 * A birthday suggestion centred in the conversation like a service message (`docs/DESIGN_SYSTEM.md` §6 "Birthday
 * suggestions", the owner's board 16b): a card on `surfaceContainerHigh` with `extraLarge` corners, at most
 * `sizes.mediaMaxWidth` wide. Its header on `tertiaryContainer` / `onTertiaryContainer` (the board's gold): the gift
 * (`ic_redeem`) on a [MaterialShapes.Cookie9Sided] badge (`sizes.headlineBadge`) in `tertiary`, "Ada suggested your
 * birthday" in `titleSmallEmphasized` and the date ("Dec 10", the language's short form) in
 * `experimentalTypography.displaySmallCondensed`, one heading for screen readers. Below it, in `bodyMedium`
 * `onSurfaceVariant`, what accepting does and which privacy setting applies. Then, while it waits for the user,
 * "Decline" (`FilledTonalButton`) and "Accept" (a default `Button`, a `LoadingIndicator` in its place while Telegram
 * answers) sharing the row, both with the press morph; once answered they collapse into "Added to your profile" with a
 * check, or "Declined", on a full pill, crossfading on the effects springs (`SizeTransform` on `defaultSpatialSpec`), a
 * polite live region. A suggestion the user sent says so, without buttons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BirthdaySuggestionCard(
    suggestion: BirthdaySuggestion,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier.widthIn(max = MaterialTheme.sizes.mediaMaxWidth),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            SuggestionHeader(suggestion)
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                Text(
                    text = suggestionText(suggestion),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!suggestion.outgoing) SuggestionAnswer(suggestion.status, onAccept, onDecline)
            }
        }
    }
}

/** The gold header: the gift badge, who suggested it and the date, one heading. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestionHeader(suggestion: BirthdaySuggestion) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val badge = MaterialShapes.Cookie9Sided.toShape()
    val name = suggestion.name.bidiIsolated()
    val title =
        if (suggestion.outgoing) {
            stringResource(R.string.birthday_suggestion_title_outgoing, name)
        } else {
            stringResource(R.string.birthday_suggestion_title, name)
        }.bidiIsolated()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.tertiaryContainer)
                .padding(horizontal = spacing.medium, vertical = spacing.large)
                .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Box(
            modifier = Modifier.size(MaterialTheme.sizes.headlineBadge).clip(badge).background(colors.tertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_redeem), contentDescription = null, tint = colors.onTertiary)
        }
        Text(
            text = title,
            color = colors.onTertiaryContainer,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        Text(
            text = shortBirthdateText(suggestion.birthdate),
            color = colors.onTertiaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = MaterialTheme.experimentalTypography.displaySmallCondensed,
        )
    }
}

/** "Decline" and "Accept", or the answer they collapsed into. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestionAnswer(
    status: BirthdaySuggestionStatus,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val answered = status == BirthdaySuggestionStatus.Accepted || status == BirthdaySuggestionStatus.Declined
    AnimatedContent(
        targetState = answered,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.defaultEffectsSpec()))
                .using(SizeTransform(sizeAnimationSpec = { _, _ -> motion.defaultSpatialSpec() }))
        },
        contentAlignment = Alignment.Center,
        label = "birthdaySuggestionAnswer",
    ) { isAnswered ->
        if (isAnswered) {
            AnswerPill(accepted = status == BirthdaySuggestionStatus.Accepted)
        } else {
            val accepting = status == BirthdaySuggestionStatus.Accepting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FilledTonalButton(
                    onClick = onDecline,
                    enabled = !accepting,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.birthday_suggestion_decline), maxLines = 1)
                }
                Button(
                    onClick = onAccept,
                    enabled = !accepting,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (accepting) {
                        LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineProgressIndicator))
                    } else {
                        Text(
                            stringResource(R.string.birthday_suggestion_accept),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                }
            }
        }
    }
}

/** "Added to your profile" with a check on `secondaryContainer`, or "Declined" on `surfaceContainerHighest`. */
@Composable
private fun AnswerPill(accepted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (accepted) colors.secondaryContainer else colors.surfaceContainerHighest)
                .padding(horizontal = spacing.medium, vertical = spacing.small)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        val content = if (accepted) colors.onSecondaryContainer else colors.onSurfaceVariant
        Text(
            text =
                stringResource(
                    if (accepted) R.string.birthday_suggestion_accepted else R.string.birthday_suggestion_declined,
                ),
            color = content,
            style = MaterialTheme.typography.labelLarge,
        )
        if (accepted) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            )
        }
    }
}

/** What accepting does, and the privacy setting that applies; for a suggestion sent, that the other person decides. */
@Composable
private fun suggestionText(suggestion: BirthdaySuggestion): String =
    when {
        suggestion.outgoing -> {
            stringResource(R.string.birthday_suggestion_text_outgoing, suggestion.name.bidiIsolated())
        }

        suggestion.privacy != null -> {
            stringResource(R.string.birthday_suggestion_text, suggestion.privacy.bidiIsolated())
        }

        else -> {
            stringResource(R.string.birthday_suggestion_text_no_privacy)
        }
    }

/** [birthdate] short, in the language's words: "Dec 10, 1815", or "Dec 10" without its year. */
@Composable
private fun shortBirthdateText(birthdate: Birthdate): String {
    val locale = LocalLocale.current.platformLocale
    val skeleton = if (birthdate.year == null) "MMMd" else "yMMMd"
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    return formatter.format(LocalDate.of(birthdate.year ?: LEAP_YEAR, birthdate.month, birthdate.day))
}

/** A year with February 29, for a birthday shown without its year. */
private const val LEAP_YEAR = 2000

/** The card's states for previews and goldens: waiting, accepting, accepted, declined, and one the user sent. */
class BirthdaySuggestionPreviewStates : PreviewParameterProvider<BirthdaySuggestion> {
    private val pending = BirthdaySuggestion(Birthdate(10, 12, null), name = "Ada", privacy = "My contacts")

    override val values =
        sequenceOf(
            pending,
            pending.copy(status = BirthdaySuggestionStatus.Accepting),
            pending.copy(status = BirthdaySuggestionStatus.Accepted),
            pending.copy(status = BirthdaySuggestionStatus.Declined),
            pending.copy(name = "Lena", outgoing = true, birthdate = Birthdate(3, 6, 1994)),
        )
}

@PingogramPreviews
@Composable
private fun BirthdaySuggestionCardPreview(
    @PreviewParameter(BirthdaySuggestionPreviewStates::class) suggestion: BirthdaySuggestion,
) {
    PingogramPreview { BirthdaySuggestionCard(suggestion, onAccept = {}, onDecline = {}) }
}
