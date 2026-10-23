package dev.kyralabs.pingogram.feature.chat.ui.translation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.TranslationEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.TranslationPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The translate bar at the top of the conversation's sheet (`REQUIREMENTS.md` FR-C3, `docs/DESIGN_SYSTEM.md` §6
 * "Translate bar"), below the pinned messages bar, as that one is: a tonal strip (`surfaceContainerHigh`, `large`
 * corners) with the translate symbol in `primary`, what a tap does in `labelLarge` `primary` ("Translate to English",
 * or "Show original" while [TranslateBar.isOn]) over the chat's language or who translates, `bodyMedium`
 * `onSurfaceVariant`, and a close button.
 *
 * - **A tap** turns the chat's translation on or off ([TranslationEvent.ToggleChat]) with the toggle haptics; the text
 *   crossfades on the effects springs, its size following on the default spatial spring.
 * - **Close** ([TranslationEvent.CloseBar]) hides the bar for this chat on this device.
 * - **Screen readers** hear one button, "Translate to English, Chat in Spanish", with closing as its custom action;
 *   the close button itself is hidden from them.
 */
@Composable
internal fun TranslateBarStrip(
    bar: TranslateBar,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val currentOnEvent by rememberUpdatedState(onEvent)
    val haptics = LocalHapticFeedback.current
    val closeLabel = stringResource(R.string.translation_bar_close)
    val isOn = bar.isOn
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainerHigh)
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(
                        if (isOn) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    currentOnEvent(TranslationEvent.ToggleChat)
                }.semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(closeLabel) {
                                currentOnEvent(TranslationEvent.CloseBar)
                                true
                            },
                        )
                }.padding(start = spacing.medium, end = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_translate),
            contentDescription = null,
            tint = colors.primary,
        )
        TranslateBarText(
            bar = bar,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = spacing.medium, top = spacing.small, bottom = spacing.small),
        )
        IconButton(
            onClick = { onEvent(TranslationEvent.CloseBar) },
            modifier = Modifier.semantics { hideFromAccessibility() },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painterResource(UiR.drawable.ic_close), contentDescription = closeLabel)
        }
    }
}

/** What a tap does over the chat's language, or who translated, crossfading as the bar turns on and off. */
@Composable
private fun TranslateBarText(
    bar: TranslateBar,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val locale = LocalConfiguration.current.locales[0]
    AnimatedContent(
        targetState = bar,
        modifier = modifier,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec())
                .togetherWith(fadeOut(motion.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
        },
        contentKey = { it.isOn },
        label = "translateBar",
    ) { shown ->
        Column {
            Text(
                text =
                    if (shown.isOn) {
                        stringResource(R.string.translation_bar_show_original)
                    } else {
                        stringResource(R.string.translation_bar_translate, languageName(shown.toLanguage, locale))
                    },
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            val from = shown.fromLanguage
            Text(
                text =
                    if (shown.isOn || from == null) {
                        stringResource(R.string.translation_bar_translated)
                    } else {
                        stringResource(R.string.translation_bar_from, languageName(from, locale))
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun TranslateBarOffPreview() {
    PingogramPreview { TranslateBarStrip(TranslationPreviewData.barOff, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun TranslateBarOnPreview() {
    PingogramPreview { TranslateBarStrip(TranslationPreviewData.barOn, onEvent = {}) }
}

/**
 * The place of the [TranslateBarStrip] at the top of the conversation's sheet, below the pinned messages bar, inset
 * from its sides and top as its rows are ([padding]: the sheet's content padding). The bar grows in and shrinks away
 * on the default spatial spring, fading on the effects springs, keeping the last bar while it leaves.
 */
@Composable
internal fun TranslateBarSlot(
    bar: TranslateBar?,
    onEvent: (ChatEvent) -> Unit,
    padding: PaddingValues,
) {
    val motion = MaterialTheme.motionScheme
    val last = remember { mutableStateOf(bar) }
    SideEffect { if (bar != null) last.value = bar }
    val shown = bar ?: last.value
    val direction = LocalLayoutDirection.current
    val small = MaterialTheme.spacing.small
    AnimatedVisibility(
        visible = bar != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        shown?.let {
            TranslateBarStrip(
                bar = it,
                onEvent = onEvent,
                modifier =
                    Modifier.padding(
                        start = padding.calculateStartPadding(direction) + small,
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(direction) + small,
                    ),
            )
        }
    }
}
