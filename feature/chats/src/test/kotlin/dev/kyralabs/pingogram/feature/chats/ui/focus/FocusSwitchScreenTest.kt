package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListHeaderActions
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ChatListScreen
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chats.ui.goldenPath
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * The chat list's Focus (board 31): the pill under the header's status line, which opens the switcher, and the
 * switcher's choices, with goldens of both; times in UTC.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FocusSwitchScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var zone: TimeZone? = null
    private val events = mutableListOf<FocusSwitchEvent>()
    private var opened = 0
    private var managed = 0

    @Before
    fun useUtc() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(zone)
    }

    @Test
    fun `the pill names the profile on now and opens the switcher`() {
        showHeader()

        val pill =
            composeRule
                .onNodeWithContentDescription("until 6:00 PM. Tap to switch.", substring = true)
                .assertHasClickAction()
        val target =
            with(composeRule.density) {
                pill
                    .fetchSemanticsNode()
                    .touchBoundsInRoot.size
                    .toDpSize()
            }
        assertTrue("$target", target.height >= 48.dp)
        pill.performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `without a profile on there is no pill`() {
        showHeader(pill = null)

        composeRule.onNodeWithContentDescription("Focus:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("12 unread · 3 mentions").assertExists()
    }

    @Test
    fun `the switcher's rows and chips are radio buttons that choose at once`() {
        showSwitcher(FocusPreviewData.workOn)

        composeRule.onNodeWithText("Work hours").assertExists()
        composeRule.onNodeWithText("Until 6:00 PM (schedule)").assertIsSelected()
        composeRule.onNodeWithText("Starts at 10:30 PM").performClick()
        composeRule.onNodeWithText("Off").performClick()
        composeRule.onNodeWithText("1 hour").performClick()
        composeRule.onNodeWithText("Manage").performClick()

        assertEquals(
            listOf(
                FocusSwitchEvent.Choose(2),
                FocusSwitchEvent.Choose(null),
                FocusSwitchEvent.Keep(FocusKeep.OneHour),
            ),
            events,
        )
        assertEquals(1, managed)
    }

    @Test
    fun `without a schedule change the choice lasts until turned off`() {
        showSwitcher(FocusPreviewData.off)

        composeRule.onNodeWithText("Until 6:00 PM").assertDoesNotExist()
        composeRule.onNodeWithText("Until I turn it off").assertIsSelected()
    }

    @Test
    fun pillLight() = capturePill(ScreenshotVariant.Light)

    @Test
    fun pillDark() = capturePill(ScreenshotVariant.Dark)

    @Test
    fun pillRtl() = capturePill(ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun pillFontScale200() = capturePill(ScreenshotVariant.Light, "fontScale200")

    @Test
    fun switcherLight() = captureSwitcher(ScreenshotVariant.Light)

    @Test
    fun switcherDark() = captureSwitcher(ScreenshotVariant.Dark)

    @Test
    fun switcherAmoled() = captureSwitcher(ScreenshotVariant.Amoled)

    @Test
    fun switcherHighContrast() = captureSwitcher(ScreenshotVariant.HighContrast)

    @Test
    fun switcherRtl() = captureSwitcher(ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun switcherFontScale200() = captureSwitcher(ScreenshotVariant.Light, "fontScale200")

    @Test
    fun switcherOffLight() = captureSwitcher(ScreenshotVariant.Light, state = FocusPreviewData.off, name = "off")

    private fun capturePill(
        variant: ScreenshotVariant,
        goldenName: String = variant.goldenName,
    ) {
        showHeader(variant = variant)
        composeRule.onRoot().captureRoboImage(goldenPath(javaClass, "pill.$goldenName"))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun captureSwitcher(
        variant: ScreenshotVariant,
        goldenName: String = variant.goldenName,
        state: FocusSwitchUiState = FocusPreviewData.workOn,
        name: String = "switcher",
    ) {
        showSwitcher(state, variant)
        composeRule.onRoot().captureRoboImage(goldenPath(javaClass, "$name.$goldenName"))
    }

    private fun showHeader(
        variant: ScreenshotVariant = ScreenshotVariant.Light,
        pill: FocusPill? = FocusPreviewData.pill,
    ) {
        val focus = ChatListFocus(pill = { pill }, onOpen = { opened++ })
        composeRule.setContent {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(LocalChatListFocus provides focus) {
                    ChatListScreen(
                        state = ChatListPreviewData.content,
                        onEvent = {},
                        summary = { ChatListPreviewData.summary },
                        headerActions =
                            ChatListHeaderActions(
                                search = GlobalSearchState(),
                                searchOpen = remember { mutableStateOf(false) },
                                openSearch = {},
                                openAccount = {},
                            ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showSwitcher(
        state: FocusSwitchUiState,
        variant: ScreenshotVariant = ScreenshotVariant.Light,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = BottomSheetDefaults.ContainerColor,
                ) {
                    Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) {
                        FocusSwitcherContent(state, onEvent = { events += it }, onManage = { managed++ })
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
