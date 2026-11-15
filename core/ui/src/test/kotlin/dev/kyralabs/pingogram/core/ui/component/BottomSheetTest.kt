package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tall sheet stops below the status bar ([PingogramBottomSheet]), where Material's own sheet, in its edge-to-edge
 * window, rises to the top of the screen. Robolectric draws no status bar, so the sheet is given one of
 * [STATUS_BAR] and the screen below paints it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BottomSheetTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration(withoutActionBar = true)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a tall sheet expands up to the status bar, not behind it`() {
        show { content ->
            PingogramBottomSheet({}, WindowInsets(top = STATUS_BAR), Modifier.testTag(SHEET), sheet()) { content() }
        }

        assertEquals(STATUS_BAR, composeRule.onNodeWithTag(SHEET).getBoundsInRoot().top)
    }

    @Test
    fun `Material's own sheet reaches the top of the window`() {
        show { content -> ModalBottomSheet({}, Modifier.testTag(SHEET), sheet()) { content() } }

        assertEquals(0.dp, composeRule.onNodeWithTag(SHEET).getBoundsInRoot().top)
    }

    @Test
    fun `a short sheet keeps its height`() {
        show(contentHeight = 200.dp) { content ->
            PingogramBottomSheet({}, WindowInsets(top = STATUS_BAR), Modifier.testTag(SHEET), sheet()) { content() }
        }

        val bounds = composeRule.onNodeWithTag(SHEET).getBoundsInRoot()
        assertEquals(true, bounds.bottom - bounds.top < 300.dp)
    }

    /** The golden: the sheet's top edge below a status bar, the screen behind it under the scrim. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun belowStatusBar() {
        show { content -> PingogramBottomSheet({}, WindowInsets(top = STATUS_BAR), sheetState = sheet()) { content() } }

        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.belowStatusBar.png")
    }

    @Composable
    private fun sheet() =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    private fun show(
        contentHeight: Dp = 2_000.dp,
        sheet: @Composable (content: @Composable () -> Unit) -> Unit,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    Box(
                        Modifier.fillMaxWidth().height(STATUS_BAR).background(MaterialTheme.colorScheme.inverseSurface),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            "9:41",
                            Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
                sheet {
                    Column(Modifier.fillMaxWidth().height(contentHeight)) {
                        Text("Send to", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLargeEmphasized)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val STATUS_BAR = 48.dp
        const val SHEET = "sheet"
    }
}
