package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R

// The parts of a command's row, which the commands sheet and the suggestions above the composer share.

/**
 * What sending [item] sends ("/start", "/start@helper_bot"), left to right whatever the layout (`numericContent`,
 * `docs/DESIGN_SYSTEM.md` §9: a command is no text of a language), its first [matched] characters after the "/" in
 * `primary` and the emphasized weight, as search highlights what it found.
 */
@Composable
internal fun CommandText(
    item: BotCommandItem,
    matched: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val match = SpanStyle(color = colors.primary, fontWeight = MaterialTheme.typography.bodyLargeEmphasized.fontWeight)
    val text = remember(item.text, matched, match) { highlighted(item.text, matched, match) }
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge.numericContent,
    )
}

/** What the bot says [item] does, in the list item's supporting style, at most [maxLines] lines; nothing without. */
@Composable
internal fun CommandDescription(
    item: BotCommandItem,
    maxLines: Int,
) {
    if (item.description.isBlank()) return
    Text(
        text = item.description,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.userContent,
    )
}

/** In a group, the avatar of the bot that takes the command, small; its placeholder ringed in [ringColor]'s row. */
@Composable
internal fun CommandBotAvatar(
    bot: CommandBot,
    ringColor: Color,
) {
    Avatar(
        photo = bot.photo,
        name = bot.name.resolve(),
        id = bot.id,
        size = MaterialTheme.sizes.avatarSmall,
        onlineRingColor = ringColor,
    )
}

/**
 * The button at a command's end that puts it into the composer's field for its arguments ([onInsert]): the fill-in
 * arrow of search suggestions, pointing to the start and up, in a 48 dp touch target; screen readers hear "Insert
 * /start" through the row's custom action instead ([commandSemantics]), so it is left out of their traversal.
 */
@Composable
internal fun InsertCommandButton(onInsert: () -> Unit) {
    IconButton(
        onClick = onInsert,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        Icon(painter = painterResource(R.drawable.ic_insert_command), contentDescription = null)
    }
}

/**
 * A command's row as screen readers hear it: "/start, Starts the bot", in a group with "Helper Bot" after it; a tap
 * sends it, and the custom action "Insert /start" puts it into the field ([onInsert]).
 */
@Composable
internal fun Modifier.commandSemantics(
    item: BotCommandItem,
    onInsert: () -> Unit,
): Modifier {
    val bot = item.bot?.name?.resolve()
    val insertLabel = stringResource(R.string.bot_command_insert, item.text)
    val resources = LocalResources.current
    return semantics {
        contentDescription = spokenList(resources, listOf(item.text, item.description, bot))
        customActions =
            listOf(
                CustomAccessibilityAction(insertLabel) {
                    onInsert()
                    true
                },
            )
    }
}

/** [text] with its first [matched] characters after the leading "/" in [match]. */
private fun highlighted(
    text: String,
    matched: Int,
    match: SpanStyle,
): AnnotatedString {
    val end = (1 + matched).coerceAtMost(text.length)
    if (matched <= 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.take(1))
        withStyle(match) { append(text.substring(1, end)) }
        append(text.substring(end))
    }
}
