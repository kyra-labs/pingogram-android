package dev.kyralabs.pingogram.feature.chat.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [isEmojiOnly]: 1 to 3 emoji and nothing else, a sequence of code points counting as the one emoji it shows. */
class EmojiOnlyTest {
    @Test
    fun `one, two or three emoji are emoji only`() {
        assertTrue(isEmojiOnly("😀"))
        assertTrue(isEmojiOnly("😀🎉"))
        assertTrue(isEmojiOnly("🌊⛴🏝"))
    }

    @Test
    fun `four emoji are too many`() {
        assertFalse(isEmojiOnly("😀😀😀😀"))
    }

    @Test
    fun `whitespace between emoji does not count`() {
        assertTrue(isEmojiOnly(" 😀 🎉\n"))
    }

    @Test
    fun `an emoji with a skin tone is one emoji`() {
        assertTrue(isEmojiOnly("👍🏽"))
        assertTrue(isEmojiOnly("👍🏻👍🏿👋🏼"))
    }

    @Test
    fun `a zero-width-joiner sequence is one emoji`() {
        // Family: man, woman, girl, boy.
        assertTrue(isEmojiOnly("👨‍👩‍👧‍👦"))
        // Woman technologist with a skin tone, three times.
        assertTrue(isEmojiOnly("👩🏾‍💻👩🏾‍💻👩🏾‍💻"))
        // Rainbow flag and heart on fire, with variation selectors inside.
        assertTrue(isEmojiOnly("🏳️‍🌈❤️‍🔥"))
    }

    @Test
    fun `a flag is one emoji, a lone regional indicator none`() {
        assertTrue(isEmojiOnly("🇯🇵"))
        assertTrue(isEmojiOnly("🇺🇦🇪🇺🇯🇵"))
        assertFalse(isEmojiOnly("🇯"))
    }

    @Test
    fun `a subdivision flag with its tags is one emoji`() {
        // England.
        assertTrue(isEmojiOnly("🏴󠁧󠁢󠁥󠁮󠁧󠁿"))
    }

    @Test
    fun `a keycap is an emoji, its digit alone is not`() {
        assertTrue(isEmojiOnly("1️⃣#️⃣"))
        assertFalse(isEmojiOnly("1"))
    }

    @Test
    fun `symbols of the miscellaneous symbols block are emoji, with or without a variation selector`() {
        assertTrue(isEmojiOnly("❤️"))
        assertTrue(isEmojiOnly("❤"))
        assertTrue(isEmojiOnly("☀️⚡"))
    }

    @Test
    fun `text-presentation symbols are emoji only with a variation selector`() {
        assertTrue(isEmojiOnly("©️"))
        assertTrue(isEmojiOnly("▶️"))
        assertFalse(isEmojiOnly("©"))
        assertFalse(isEmojiOnly("™"))
        assertFalse(isEmojiOnly("→"))
    }

    @Test
    fun `an emoji asked to show as text is not emoji only`() {
        assertFalse(isEmojiOnly("☺︎"))
    }

    @Test
    fun `text with emoji is not emoji only`() {
        assertFalse(isEmojiOnly("lol 😂"))
        assertFalse(isEmojiOnly("😂!"))
        assertFalse(isEmojiOnly("😂1"))
        assertFalse(isEmojiOnly("ok"))
    }

    @Test
    fun `an empty or blank text is not emoji only`() {
        assertFalse(isEmojiOnly(""))
        assertFalse(isEmojiOnly("  \n"))
    }
}
