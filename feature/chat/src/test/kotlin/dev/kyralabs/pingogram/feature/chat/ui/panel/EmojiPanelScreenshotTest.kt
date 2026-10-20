package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerPanelState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.PanelPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the emoji and sticker panel on a compact phone, light and dark (`docs/TESTING.md`): open below the
 * composer on its emoji tab (the composer's button shows the keyboard icon), and the stickers tab with sections, while
 * it cannot load, and without stickers. Sticker files are not on the device, so stickers show their emoji. Brand
 * colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class EmojiPanelScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        if (stateName == "emoji") {
                            ChatBottomBar(
                                composer =
                                    ComposerUiState.Available(
                                        canSchedule = true,
                                        attachOptions = persistentListOf(AttachOption.Gallery),
                                    ),
                                text = remember { ComposerText() },
                                onSend = {},
                                onFocusChanged = {},
                                panel = PanelPreviewData.content,
                                panelState = remember { ComposerPanelState(open = true) },
                            )
                        } else {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                                EmojiPanel(
                                    state = STATES.getValue(stateName),
                                    onEmoji = {},
                                    onBackspace = {},
                                    onSticker = {},
                                    onEvent = {},
                                    modifier = Modifier.fillMaxWidth().height(MaterialTheme.sizes.inputPanelHeight),
                                    initialTab = PanelTab.Stickers,
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private val STATES =
            mapOf(
                "stickers" to PanelPreviewData.content,
                "stickersError" to PanelPreviewData.stickersError,
                "stickersEmpty" to PanelPreviewData.stickersEmpty,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<String> = listOf("emoji") + STATES.keys
    }
}
