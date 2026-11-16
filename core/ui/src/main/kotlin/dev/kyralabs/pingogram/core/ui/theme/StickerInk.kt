package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colors of the stickers the user makes from a photo (boards 14a, 14b): artwork, not the app's chrome. A sticker
 * goes to other people and shows on every theme, light or dark, so it cannot take the theme's roles, as the
 * encryption key's image does not (`docs/DESIGN_SYSTEM.md` §2.2): stickers are drawn white-edged, so they stand apart
 * from any chat's wallpaper and bubble, and their text is white with a near-black edge, readable on any photo.
 */
object StickerInk {
    /** The outline around the sticker's photo. */
    val Outline = Color.White

    /** Text drawn on a sticker. */
    val Text = Color.White

    /** The edge around a sticker's text: neutral tone 10 of the brand's palette, never pure black. */
    val TextEdge = Color(0xFF1A1B20)
}
