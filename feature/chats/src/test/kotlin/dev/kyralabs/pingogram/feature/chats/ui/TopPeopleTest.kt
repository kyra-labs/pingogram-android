package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search's top people at large font scales (audit A13): from 150% a long name takes a second line rather than being
 * cut after a few letters, and at 100% every name keeps one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TopPeopleTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun `at 200 percent a long name takes two lines`() {
        setPeople()

        assertTrue("${height(LONG)} > ${height(SHORT)}", height(LONG) > height(SHORT))
    }

    @Test
    fun `at 100 percent every name keeps one line`() {
        setPeople()

        assertEquals(height(SHORT), height(LONG))
    }

    private fun setPeople() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                TopPeople(people = persistentListOf(person(1, SHORT), person(2, LONG)), onOpen = {})
            }
        }
    }

    private fun height(name: String): Int =
        composeRule
            .onNode(hasContentDescription(name))
            .fetchSemanticsNode()
            .size.height

    private fun person(
        id: Long,
        name: String,
    ) = SearchChatItem(
        id = id,
        title = UiText.Plain(name),
        photo = null,
        placeholderIcon = null,
        isOnline = false,
        badge = null,
        isSecret = false,
        isMuted = false,
        subtitle = null,
    )

    private companion object {
        const val SHORT = "Al"
        const val LONG = "Alexandra Konstantinopoulou"
    }
}
