package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.withMatches

/** [label] ("Ada:") in [labelColor], then [text] with every match of [highlight] in [match]. */
internal fun highlighted(
    label: String?,
    text: String,
    highlight: MatchHighlight,
    labelColor: Color,
    match: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        if (label != null) {
            withStyle(SpanStyle(color = labelColor)) { append(label) }
            append(' ')
        }
        val start = length
        append(text)
        highlight.ranges(text).forEach { range -> addStyle(match, start + range.first, start + range.last + 1) }
    }

/**
 * [text] after [label] (the sender, in [labelStyle]), its matches of [highlight] as blocks for `textBlocks` to draw
 * (`withMatches`) in [matchStyle], whose color the blocks' text takes: the experimental look's found message
 * (`docs/DESIGN_SYSTEM.md` §13.4). Only the text is searched for matches, never the label.
 */
internal fun blockHighlighted(
    label: String?,
    text: String,
    highlight: MatchHighlight,
    labelStyle: SpanStyle,
    matchStyle: SpanStyle,
): AnnotatedString {
    val matched = AnnotatedString(text).withMatches(highlight, matchStyle.color)
    return buildAnnotatedString {
        if (label != null) {
            withStyle(labelStyle) { append(label) }
            append(' ')
        }
        val start = length
        append(matched)
        highlight.ranges(text).forEach { range -> addStyle(matchStyle, start + range.first, start + range.last + 1) }
    }
}
