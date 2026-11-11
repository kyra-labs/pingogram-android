package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.time.BirthdateDialog
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The offer to suggest a birthday to a contact without one (`docs/DESIGN_SYSTEM.md` §6 "Birthday suggestions", the
 * owner's board 16a), a segment of "Info" in its [shapes] on `tertiaryContainer` (the board's gold): the gift on a
 * [MaterialShapes.Sunny] badge (`sizes.avatarRow`) in `tertiary`, "No birthday on Lena's profile" in
 * `titleSmallEmphasized` over "Suggest one. Lena decides whether to keep it and who sees it." in `bodyMedium`, in
 * `onTertiaryContainer`, one item for screen readers; then "Suggest a birthday", a default `Button` across the segment
 * in `tertiary` / `onTertiary` with the cake symbol, a `LoadingIndicator` in its place while Telegram answers. It opens
 * `core:ui`'s [BirthdateDialog] titled "Suggest a birthday", whose "Suggest" sends the date
 * ([ProfileEvent.SuggestBirthdate]); whether it is open survives rotation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BirthdaySuggestionRow(
    offer: BirthdaySuggestionOffer,
    shapes: ListItemShapes,
    onEvent: (ProfileEvent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val name = offer.firstName.bidiIsolated()
    var picking by rememberSaveable { mutableStateOf(false) }
    Surface(shape = shapes.shape, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                SunnyBadge()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    Text(
                        stringResource(R.string.profile_birthday_suggest_title, name).bidiIsolated(),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        stringResource(R.string.profile_birthday_suggest_text, name).bidiIsolated(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            SuggestButton(offer.isSending) { picking = true }
        }
    }
    if (picking) {
        BirthdateDialog(
            birthdate = null,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                onEvent(ProfileEvent.SuggestBirthdate(it))
            },
            title = stringResource(R.string.profile_birthday_suggest_action),
            confirm = stringResource(R.string.profile_birthday_suggest_confirm),
        )
    }
}

/** "Suggest a birthday" across the segment in `tertiary`, a `LoadingIndicator` in its label's place while sending. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestButton(
    isSending: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = !isSending,
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary),
    ) {
        if (isSending) {
            LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineProgressIndicator))
        } else {
            Icon(
                painter = painterResource(UiR.drawable.ic_cake_add),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                stringResource(R.string.profile_birthday_suggest_action),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** The gift on a sunny badge in `tertiary`; decorative. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SunnyBadge() {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialShapes.Sunny.toShape()
    Box(
        modifier = Modifier.size(MaterialTheme.sizes.avatarRow).clip(shape).background(colors.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(UiR.drawable.ic_redeem), contentDescription = null, tint = colors.onTertiary)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PingogramPreviews
@Composable
private fun BirthdaySuggestionRowPreview() {
    PingogramPreview {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            BirthdaySuggestionRow(BirthdaySuggestionOffer("Lena"), ListItemDefaults.segmentedShapes(0, 1), onEvent = {})
            BirthdaySuggestionRow(
                BirthdaySuggestionOffer("Lena", isSending = true),
                ListItemDefaults.segmentedShapes(0, 1),
                onEvent = {},
            )
        }
    }
}
