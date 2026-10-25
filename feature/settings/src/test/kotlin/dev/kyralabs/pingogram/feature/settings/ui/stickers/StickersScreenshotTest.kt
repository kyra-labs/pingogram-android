package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickersPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Stickers and its Trending and Archived pages in each of their states, light and dark; the sets and the
 * Emoji page in AMOLED black, high contrast, right to left, a 700 dp window and at twice the font size
 * (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StickersScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentLight() = capture("contentLight", ThemeMode.Light, StickersPreviewStates.content)

    @Test
    fun contentDark() = capture("contentDark", ThemeMode.Dark, StickersPreviewStates.content)

    @Test
    fun noSetsLight() = capture("noSetsLight", ThemeMode.Light, StickersPreviewStates.noSets)

    @Test
    fun noSetsDark() = capture("noSetsDark", ThemeMode.Dark, StickersPreviewStates.noSets)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, StickersUiState.Loading)

    @Test
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, StickersUiState.Loading)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, StickersUiState.Error(DomainError.Network))

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, StickersUiState.Error(DomainError.Network))

    @Test
    fun trendingLight() =
        captureList("trendingLight", ThemeMode.Light, archived = false, StickersPreviewStates.trending)

    @Test
    fun trendingDark() = captureList("trendingDark", ThemeMode.Dark, archived = false, StickersPreviewStates.trending)

    @Test
    fun archivedLight() = captureList("archivedLight", ThemeMode.Light, archived = true, StickersPreviewStates.archived)

    @Test
    fun archivedDark() = captureList("archivedDark", ThemeMode.Dark, archived = true, StickersPreviewStates.archived)

    @Test
    fun noArchivedLight() =
        captureList("noArchivedLight", ThemeMode.Light, archived = true, StickersPreviewStates.noArchived)

    @Test
    fun noArchivedDark() =
        captureList("noArchivedDark", ThemeMode.Dark, archived = true, StickersPreviewStates.noArchived)

    @Test
    fun listErrorLight() =
        captureList("listErrorLight", ThemeMode.Light, false, StickerSetListUiState.Error(DomainError.Network))

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, StickersPreviewStates.content)

    @Test
    fun emojiLight() = composeRule.captureScreen(javaClass, "emojiLight", ThemeMode.Light) { EmojiSets() }

    @Test
    fun emojiDark() = composeRule.captureScreen(javaClass, "emojiDark", ThemeMode.Dark) { EmojiSets() }

    @Test
    fun emojiAmoled() = captureEmoji(ScreenshotVariant.Amoled)

    @Test
    fun emojiHighContrast() = captureEmoji(ScreenshotVariant.HighContrast)

    @Test
    fun emojiRtl() = captureEmoji(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun emojiMedium() = captureEmoji(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun emojiFontScale200() = composeRule.captureScreen(javaClass, "emojiFontScale200", ThemeMode.Light) { EmojiSets() }

    @Test
    fun trendingEmojiLight() =
        composeRule.captureScreen(javaClass, "trendingEmojiLight", ThemeMode.Light) {
            StickerSetListScreen(
                archived = false,
                state = StickersPreviewStates.trendingEmoji,
                onEvent = {},
                onBack = {},
                kind = StickerSetKind.Emoji,
            )
        }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun trendingFontScale200() =
        captureList("trendingFontScale200", ThemeMode.Light, archived = false, StickersPreviewStates.trending)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: StickersUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        StickersScreen(state = state, onEvent = {}, onOpenList = {}, onShare = {}, onBack = {})
    }

    private fun captureList(
        name: String,
        themeMode: ThemeMode,
        archived: Boolean,
        state: StickerSetListUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        StickerSetListScreen(archived = archived, state = state, onEvent = {}, onBack = {})
    }

    private fun captureEmoji(variant: ScreenshotVariant) =
        composeRule.captureScreen(
            javaClass,
            "emoji" + variant.goldenName.replaceFirstChar { it.uppercase() },
            variant,
        ) {
            EmojiSets()
        }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            StickersScreen(StickersPreviewStates.content, onEvent = {}, onOpenList = {}, onShare = {}, onBack = {})
        }
}

/** The Emoji page with the user's custom emoji sets. */
@Composable
private fun EmojiSets() {
    StickersScreen(
        StickersPreviewStates.emoji,
        onEvent = {},
        onOpenList = {},
        onShare = {},
        onBack = {},
        kind = StickerSetKind.Emoji,
    )
}
