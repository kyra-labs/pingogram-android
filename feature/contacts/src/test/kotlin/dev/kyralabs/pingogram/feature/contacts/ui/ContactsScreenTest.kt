package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Contacts tab's screen with fixed states: its heading, the search field, rows and their menu, the actions above
 * them, the delete question, and the fast-scroll index by touch and for screen readers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ContactsScreenTest {
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

    private val events = mutableListOf<ContactsEvent>()
    private val syncEvents = mutableListOf<ContactSyncEvent>()
    private var added = 0
    private val edited = mutableListOf<Long>()
    private lateinit var listState: LazyListState

    @Test
    fun `the top bar names the tab as a heading`() {
        setScreen(ContactsPreviewData.content)

        composeRule.onNode(hasText(string(R.string.contacts_title)) and isHeading()).assertIsDisplayed()
    }

    @Test
    fun `typing into the search field reports the query`() {
        setScreen(ContactsPreviewData.content)

        composeRule.onNode(hasSetTextAction()).performTextInput("gra")
        composeRule.waitForIdle()

        assertEquals(ContactsEvent.QueryChanged("gra"), events.last())
    }

    @Test
    fun `a contact opens its chat, and the actions above add and invite`() {
        setScreen(ContactsPreviewData.content)

        composeRule.onNode(hasContentDescription("Grace Hopper", substring = true) and hasClickAction()).performClick()
        composeRule.onNodeWithText(string(R.string.contacts_add)).performClick()
        composeRule.onNodeWithText(string(R.string.contacts_invite)).performClick()

        assertEquals(listOf(ContactsEvent.OpenChat(4), ContactsEvent.Invite), events)
        assertEquals(1, added)
    }

    @Test
    fun `a long press offers Edit and Delete`() {
        setScreen(ContactsPreviewData.content)
        val grace = hasContentDescription("Grace Hopper", substring = true) and hasClickAction()

        composeRule.onNode(grace).performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.contacts_edit)).performClick()
        composeRule.onNode(grace).performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.contacts_delete)).performClick()

        assertEquals(listOf(4L), edited)
        assertEquals(listOf<ContactsEvent>(ContactsEvent.Delete(4)), events)
    }

    @Test
    fun `screen readers get Edit and Delete as actions of the row`() {
        setScreen(ContactsPreviewData.content)
        val grace = composeRule.onNode(hasContentDescription("Grace Hopper", substring = true) and hasClickAction())

        val actions = grace.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.first { it.label == string(R.string.contacts_delete) }.action()

        assertEquals(listOf(string(R.string.contacts_edit), string(R.string.contacts_delete)), actions.map { it.label })
        assertEquals(listOf<ContactsEvent>(ContactsEvent.Delete(4)), events)
    }

    @Test
    fun `deleting asks first, and the answer is reported`() {
        setScreen(ContactsPreviewData.deleting)

        composeRule.onNodeWithText(string(R.string.contacts_delete_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.contacts_dialog_cancel)).performClick()
        composeRule.onNodeWithText(string(R.string.contacts_delete_confirm)).performClick()

        assertEquals(listOf(ContactsEvent.DismissDialog, ContactsEvent.ConfirmDelete), events)
    }

    @Test
    fun `touching the index jumps to the section under the finger`() {
        setScreen(ContactsPreviewData.long)
        val index = composeRule.onNodeWithContentDescription(string(R.string.contacts_index))

        // The middle of section M, the 13th of 26.
        index.performTouchInput {
            down(percentOffset(0.5f, (SECTION_M + 0.5f) / SECTIONS))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(headerOf(SECTION_M), listState.firstVisibleItemIndex)
    }

    @Test
    fun `screen readers hear the index as an adjustable letter and can move it`() {
        setScreen(ContactsPreviewData.long)
        val index = composeRule.onNodeWithContentDescription(string(R.string.contacts_index))
        index.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "A"))

        index.performSemanticsAction(SemanticsActions.SetProgress) { it(SECTION_F.toFloat()) }
        composeRule.waitForIdle()

        assertEquals(headerOf(SECTION_F), listState.firstVisibleItemIndex)
        index.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "F"))
    }

    @Test
    fun `a short list has no index`() {
        setScreen(ContactsPreviewData.content)

        composeRule.onNodeWithContentDescription(string(R.string.contacts_index)).assertDoesNotExist()
    }

    @Test
    fun `a search without matches says so`() {
        setScreen(ContactsPreviewData.noResults)

        composeRule.onNodeWithText(string(R.string.contacts_no_results)).assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `without contacts the empty state offers to add one`() {
        setScreen(ContactsPreviewData.empty)

        composeRule.onNode(hasText(string(R.string.contacts_add)) and hasClickAction()).performClick()

        assertEquals(1, added)
    }

    @Test
    fun `an error offers to try again`() {
        setScreen(ContactsPreviewData.error)

        composeRule.onNodeWithText(string(R.string.contacts_retry)).performClick()

        assertEquals(listOf<ContactsEvent>(ContactsEvent.Retry), events)
    }

    @Test
    fun `the offer of contacts sync turns it on, or goes with Not now`() {
        setScreen(ContactsPreviewData.syncOffer)

        composeRule.onNodeWithText(string(R.string.contacts_sync_turn_on)).performClick()
        composeRule.onNodeWithText(string(R.string.contacts_sync_not_now)).performClick()

        assertEquals(listOf<ContactSyncEvent>(ContactSyncEvent.TurnOn), syncEvents)
        assertEquals(listOf<ContactsEvent>(ContactsEvent.DismissSyncOffer), events)
    }

    @Test
    fun `while sync is on, Sync now syncs and a phone's contact can be invited`() {
        setScreen(ContactsPreviewData.syncOn)

        composeRule.onNodeWithText(string(R.string.contacts_sync_now)).performClick()
        composeRule
            .onNodeWithContentDescription(string(R.string.contacts_invite_person, "Lin Example"))
            .performScrollTo()
            .performClick()

        assertEquals(listOf<ContactSyncEvent>(ContactSyncEvent.SyncNow), syncEvents)
        assertEquals(listOf<ContactsEvent>(ContactsEvent.InvitePhoneContact("+15550100")), events)
    }

    @Test
    fun `a paused sync asks to allow again, or to turn off`() {
        setScreen(ContactsPreviewData.syncPaused)

        composeRule.onNodeWithText(string(R.string.contacts_sync_allow)).performClick()
        composeRule.onNodeWithText(string(R.string.contacts_sync_turn_off)).performClick()

        assertEquals(listOf(ContactSyncEvent.Allow, ContactSyncEvent.TurnOff), syncEvents)
    }

    private fun setScreen(state: ContactsUiState) {
        composeRule.setContent {
            listState = rememberLazyListState()
            PingogramTheme(dynamicColor = false) {
                ContactsScreen(
                    state = state,
                    onEvent = { events += it },
                    onAddContact = { added++ },
                    onEditContact = { edited += it },
                    onSyncEvent = { syncEvents += it },
                    listState = listState,
                )
            }
        }
    }

    /** The list index of section [section]'s header in the long list: two actions, then 3 entries per section. */
    private fun headerOf(section: Int) = 2 + section * 3

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private fun string(
        @StringRes id: Int,
        argument: String,
    ): String = RuntimeEnvironment.getApplication().getString(id, argument)

    private companion object {
        const val SECTIONS = 26
        const val SECTION_F = 5
        const val SECTION_M = 12
    }
}
