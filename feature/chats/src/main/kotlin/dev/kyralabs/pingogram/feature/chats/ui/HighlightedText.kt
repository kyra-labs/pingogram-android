package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight

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
