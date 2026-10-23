package dev.kyralabs.pingogram.core.ui.sticker

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.text.TextBlocksLayout
import dev.kyralabs.pingogram.core.ui.text.customEmoji
import dev.kyralabs.pingogram.core.ui.text.customEmojiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An animated (TGS) custom emoji in a text, drawn by [customEmoji] through [AnimatedStickers] (the stickers' decoder
 * and frame cache), on light and dark surfaces: its first frame over the fallback emoji, in `bodyLarge` and in
 * `displaySmall` as an emoji-only message shows it, each size's first frame in the stickers' frame cache. Captured once
 * both frames are there: they render off the composition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CustomEmojiTextScreenshotTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule(order = 2)
    val folder = TemporaryFolder()

    @Test
    fun animatedLight() = capture("animatedLight", ThemeMode.Light)

    @Test
    fun animatedDark() = capture("animatedDark", ThemeMode.Dark)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
    ) {
        val files = FakeStickerFiles().apply { state.value = FileState.Downloaded(pulseTgs(folder.root).path, 100) }
        // Previews and screenshots show the first frame only.
        val stickers = AnimatedStickers(files, LottieStickerDecoder(), Dispatchers.Unconfined, animate = false)
        val renderer = CustomEmojiRenderer(stickers, stills = { _, _ -> null })
        renderer.emoji = persistentMapOf(PULSE to pulse)
        val inText =
            FormattedText("Ready? 🎉 Go!", persistentListOf(TextEntity(7, 2, TextEntityType.CustomEmoji(PULSE))))
        val alone = FormattedText("🎉", persistentListOf(TextEntity(0, 2, TextEntityType.CustomEmoji(PULSE))))
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(MaterialTheme.spacing.large)) {
                        for ((text, style) in listOf(
                            inText to MaterialTheme.typography.bodyLarge,
                            alone to MaterialTheme.typography.displaySmall,
                        )) {
                            val layout = remember { TextBlocksLayout() }
                            val color = MaterialTheme.colorScheme.onSurface
                            Text(
                                text = remember(text) { text.customEmojiText() },
                                modifier = Modifier.customEmoji(renderer, layout, { color }),
                                color = color,
                                style = style,
                                onTextLayout = layout.onTextLayout,
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { stickers.firstFrameCount == 2 }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/${javaClass.name}.$name.png")
    }

    private companion object {
        const val PULSE = 42L
        const val TIMEOUT_MILLIS = 5_000L

        val pulse =
            CustomEmoji(
                id = PULSE,
                sticker =
                    Sticker(
                        setId = 1,
                        width = 100,
                        height = 100,
                        emoji = "🎉",
                        format = StickerFormat.Tgs,
                        thumbnail = null,
                        file = FileRef(1, size = 100),
                    ),
                needsRepainting = false,
            )
    }
}
