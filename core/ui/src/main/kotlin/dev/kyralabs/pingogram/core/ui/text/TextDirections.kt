package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

/**
 * This style for text that comes from people or from Telegram rather than from Pingogram's own strings: messages and
 * captions, link previews, chat titles and previews, names, usernames, sponsored messages, and formatted times and
 * dates (`docs/DESIGN_SYSTEM.md` §9). Its direction follows its first strong character ([TextDirection.Content]), as
 * Android's `TextView` sets text, so English in a right-to-left layout keeps its punctuation at its end ("Thanks!",
 * "9:41 AM", "@ada") and Hebrew or Arabic in a left-to-right layout reads right to left; text without a strong
 * character (digits) takes the layout's direction. Each paragraph of the text decides for itself.
 *
 * Compose sets text without a direction in the layout's direction instead, which is right for Pingogram's own strings,
 * so they keep it. The alignment (`TextAlign.Start` by default) follows the paragraph's direction: in a row that fills
 * the width, English in a right-to-left layout starts at the left, as in Google's apps.
 */
val TextStyle.userContent: TextStyle
    get() = if (textDirection == TextDirection.Content) this else copy(textDirection = TextDirection.Content)

/**
 * This style for content that may hold no letters at all, such as phone numbers ("+44 7911 123456") or coordinates
 * ("51.50735, -0.12776"): as [userContent], but left to right without a strong character
 * ([TextDirection.ContentOrLtr]), as a right-to-left paragraph would split such numbers at their spaces and commas and
 * reverse their order.
 */
val TextStyle.numericContent: TextStyle
    get() = if (textDirection == TextDirection.ContentOrLtr) this else copy(textDirection = TextDirection.ContentOrLtr)
