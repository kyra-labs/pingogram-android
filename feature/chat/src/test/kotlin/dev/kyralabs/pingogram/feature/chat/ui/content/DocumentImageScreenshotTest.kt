package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A photo sent as a file (`REQUIREMENTS.md` FR-C4): its thumbnail in the file row's icon place ([DocumentThumbnail]),
 * in both bubbles, light and dark. Inspection mode, so Coil shows the preview's solid image.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DocumentImageScreenshotTest {
    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val sample =
            ContentSample("documentImage", SampleBubbles.Both) { colors, _ ->
                MessageDocument(
                    content = ContentPreviewData.imageDocument,
                    fileState = FileState.Downloaded(path = "", size = 0),
                    onDownload = {},
                    onCancel = {},
                    onOpen = {},
                    colors = colors,
                )
            }
        captureRoboImage("src/test/snapshots/${javaClass.name}.$variant.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    Surface(color = MaterialTheme.colorScheme.background) { ContentSampleFrame(sample) }
                }
            }
        }
    }
}
