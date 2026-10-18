package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import dev.kyralabs.pingogram.feature.chats.R as ChatsR

/**
 * Archiving and unarchiving from the chat lists (FR-L4, FR-L9), from the swipe or the snackbar's "Undo" to the TDLib
 * request: in All chats, in a folder that keeps archived chats, and in Archived chats. The chat list runs its real view
 * model and repositories on a [FakeTdlibClient] bound with Hilt; [ChatListsScript] answers the requests with the
 * updates TDLib sends.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ChatListArchiveTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatListsScript::install)

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    // All chats

    @Test
    fun `swiping a chat in All chats archives it once`() {
        setApp()

        row(GRACE_TITLE).performTouchInput { swipeLeft() }

        awaitText(ChatsR.string.chat_list_archived_one)
        awaitNoRow(GRACE_TITLE)
        assertEquals(listOf("$GRACE → archive"), listRequests())
    }

    @Test
    fun `undo in All chats moves the chat back, and it stays`() {
        setApp()
        row(GRACE_TITLE).performTouchInput { swipeLeft() }
        awaitNoRow(GRACE_TITLE)

        undo()

        awaitRow(GRACE_TITLE)
        assertEquals(listOf("$GRACE → archive", "$GRACE → main"), listRequests())
        assertEquals(ChatListsScript.MAIN, ChatListsScript.folderOf(GRACE))
    }

    @Test
    fun `undo in All chats works when TDLib takes the chat out of the main list first`() {
        ChatListsScript.removesFirst = true
        setApp()
        row(GRACE_TITLE).performTouchInput { swipeLeft() }
        awaitNoRow(GRACE_TITLE)

        undo()

        awaitRow(GRACE_TITLE)
        assertEquals(listOf("$GRACE → archive", "$GRACE → main"), listRequests())
    }

    // A folder that keeps archived chats

    @Test
    fun `swiping a chat in a folder archives it once, and the chat stays there`() {
        setApp()
        selectFolder()

        row(ADA_TITLE).performTouchInput { swipeLeft() }

        awaitRowAction(ADA_TITLE, ChatsR.string.chat_list_action_unarchive)
        awaitText(ChatsR.string.chat_list_archived_one)
        assertEquals(listOf("$ADA → archive"), listRequests())
    }

    @Test
    fun `undo in a folder moves the chat back to the main list`() {
        setApp()
        selectFolder()
        row(ADA_TITLE).performTouchInput { swipeLeft() }
        awaitRowAction(ADA_TITLE, ChatsR.string.chat_list_action_unarchive)

        undo()

        awaitRowAction(ADA_TITLE, ChatsR.string.chat_list_action_archive)
        assertEquals(listOf("$ADA → archive", "$ADA → main"), listRequests())
        assertEquals(ChatListsScript.MAIN, ChatListsScript.folderOf(ADA))
    }

    // Archived chats

    @Test
    fun `swiping a chat in Archived chats unarchives it once`() {
        setApp()
        openArchive()

        row(ALAN_TITLE).performTouchInput { swipeLeft() }

        awaitNoRow(ALAN_TITLE)
        assertEquals(listOf("$ALAN → main"), listRequests())
    }

    @Test
    fun `an archived chat just marked as read by swiping unarchives when swiped`() {
        setApp()
        openArchive()
        row(ALAN_TITLE).performTouchInput { swipeRight() }
        awaitRowAction(ALAN_TITLE, ChatsR.string.chat_list_action_mark_unread)

        row(ALAN_TITLE).performTouchInput { swipeLeft() }

        awaitNoRow(ALAN_TITLE)
        assertEquals(listOf("$ALAN read", "$ALAN → main"), readRequests() + listRequests())
    }

    private fun setApp() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn, navController = rememberNavController())
            }
        }
        awaitRow(ADA_TITLE)
    }

    private fun selectFolder() {
        composeRule.onNode(hasContentDescription(FOLDER_TITLE) and hasClickAction()).performClick()
        awaitNoRow(BABBAGE_TITLE)
    }

    private fun openArchive() {
        // The archive row comes with the archive's first chats, which may load after the main list's.
        val archive = string(ChatsR.string.chat_list_archived_chats)
        awaitRow(archive)
        row(archive).performClick()
        awaitRow(ALAN_TITLE)
    }

    private fun undo() {
        awaitText(ChatsR.string.chat_list_undo)
        composeRule.onNode(hasText(string(ChatsR.string.chat_list_undo))).performClick()
    }

    /** `addChatToList` requests, in order: "102 → archive" archived chat 102. */
    private fun listRequests(): List<String> {
        composeRule.waitForIdle()
        return tdlib.sentOf<TdApi.AddChatToList>().map {
            val list = if (it.chatList is TdApi.ChatListArchive) "archive" else "main"
            "${it.chatId} → $list"
        }
    }

    /** Requests that read a chat or mark it as unread: "103 read", "103 unread". */
    private fun readRequests(): List<String> {
        composeRule.waitForIdle()
        return tdlib.sent.mapNotNull {
            when (it) {
                is TdApi.ViewMessages -> "${it.chatId} read"
                is TdApi.ToggleChatIsMarkedAsUnread -> "${it.chatId} ${if (it.isMarkedAsUnread) "unread" else "read"}"
                else -> null
            }
        }
    }

    /** A chat's row: its description starts with its title, unlike the archive row's, which may name it too. */
    private fun rowMatcher(title: String) =
        SemanticsMatcher("row of $title") { node ->
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .any { it.startsWith("$title,") }
        } and hasClickAction()

    private fun row(title: String): SemanticsNodeInteraction = composeRule.onNode(rowMatcher(title))

    private fun awaitRow(title: String) = await { exists(rowMatcher(title)) }

    private fun awaitNoRow(title: String) = await { !exists(rowMatcher(title)) }

    /** Waits until [title]'s row offers the action labelled [label] to screen readers, as its swipe does. */
    private fun awaitRowAction(
        title: String,
        @StringRes label: Int,
    ) {
        val name = string(label)
        await {
            composeRule.onAllNodes(rowMatcher(title)).fetchSemanticsNodes().any { node ->
                node.config
                    .getOrNull(SemanticsActions.CustomActions)
                    .orEmpty()
                    .any { it.label == name }
            }
        }
    }

    private fun awaitText(
        @StringRes id: Int,
    ) = await { exists(hasText(string(id))) }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The lists get their state from a background dispatcher. */
    private fun await(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MILLIS, condition)
        composeRule.waitForIdle()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val ADA = 101L
        const val GRACE = 102L
        const val ALAN = 103L
        const val BABBAGE = 104L

        const val ADA_TITLE = "Ada Lovelace"
        const val GRACE_TITLE = "Grace Hopper"
        const val ALAN_TITLE = "Alan Turing"
        const val BABBAGE_TITLE = "Charles Babbage"
        const val FOLDER_TITLE = "Work"

        const val TIMEOUT_MILLIS = 5_000L
    }

    /**
     * Four private chats as TDLib reports them: Ada, Grace and Charles in the main list, Alan (unread) in the archive,
     * and a folder, Work, of Ada, Grace and Alan that keeps archived chats. `addChatToList`, `viewMessages` and
     * `toggleChatIsMarkedAsUnread` change them as TDLib does, with the updates it sends before it answers
     * ([removesFirst]).
     */
    private object ChatListsScript {
        /** TDLib's folders of chats: the main list and the archive, by their chat list's constructor. */
        const val MAIN = TdApi.ChatListMain.CONSTRUCTOR
        private const val ARCHIVE = TdApi.ChatListArchive.CONSTRUCTOR

        private val folders = ConcurrentHashMap<Long, Int>()
        private val orders = mapOf(ADA to 4L, GRACE to 3L, BABBAGE to 2L, ALAN to 1L)
        private val inFolder = setOf(ADA, GRACE, ALAN)
        private val titles = mapOf(ADA to ADA_TITLE, GRACE to GRACE_TITLE, ALAN to ALAN_TITLE, BABBAGE to BABBAGE_TITLE)

        /**
         * The order of TDLib's updates when a chat moves between the main list and the archive, which follows a hash
         * map: false sends the new position first, so the chat is in both lists for a moment; true takes it out of the
         * old list first.
         */
        @Volatile
        var removesFirst = false

        fun folderOf(chatId: Long): Int? = folders[chatId]

        fun install(tdlib: FakeTdlibClient) {
            folders.clear()
            removesFirst = false
            orders.keys.forEach { folders[it] = if (it == ALAN) ARCHIVE else MAIN }
            val announced = AtomicBoolean(false)
            tdlib.on(TdApi.LoadChats::class) {
                if (announced.compareAndSet(false, true)) {
                    tdlib.emit(TdApiFixtures.updateChatFolders(TdApiFixtures.chatFolderInfo(1, FOLDER_TITLE)))
                    orders.keys.forEach { id ->
                        val (first, last) = titles.getValue(id).split(" ")
                        tdlib.emit(
                            TdApiFixtures.updateUser(TdApiFixtures.user(id = id, firstName = first, lastName = last)),
                        )
                        tdlib.emit(TdApiFixtures.updateNewChat(chat(id)))
                    }
                    TdResult.Success(TdApi.Ok())
                } else {
                    // TDLib's answer once a chat list is loaded completely.
                    TdResult.Failure(TdError(404, "Not Found"))
                }
            }
            tdlib.on(TdApi.AddChatToList::class) { request ->
                val id = request.chatId
                val folder = request.chatList.constructor
                val old = folders.put(id, folder)
                if (old != folder) {
                    val added = TdApi.UpdateChatPosition(id, position(folder, orders.getValue(id)))
                    val removed = TdApi.UpdateChatPosition(id, position(checkNotNull(old), order = 0))
                    (if (removesFirst) listOf(removed, added) else listOf(added, removed)).forEach { tdlib.emit(it) }
                }
                TdResult.Success(TdApi.Ok())
            }
            tdlib.on(TdApi.ViewMessages::class) { request ->
                tdlib.emit(TdApi.UpdateChatReadInbox(request.chatId, lastMessageId(request.chatId), 0))
                tdlib.emit(TdApi.UpdateChatIsMarkedAsUnread(request.chatId, false))
                TdResult.Success(TdApi.Ok())
            }
            tdlib.on(TdApi.ToggleChatIsMarkedAsUnread::class) { request ->
                tdlib.emit(TdApi.UpdateChatIsMarkedAsUnread(request.chatId, request.isMarkedAsUnread))
                TdResult.Success(TdApi.Ok())
            }
        }

        private fun chat(id: Long): TdApi.Chat {
            val order = orders.getValue(id)
            val positions =
                listOfNotNull(
                    position(folders.getValue(id), order),
                    if (id in inFolder) TdApiFixtures.chatPosition(TdApi.ChatListFolder(1), order) else null,
                )
            val unreadCount = if (id == ALAN) 2 else 0
            return TdApiFixtures.chat(
                id = id,
                type = TdApi.ChatTypePrivate(id),
                title = titles.getValue(id),
                lastMessage =
                    TdApiFixtures.message(
                        id = lastMessageId(id),
                        chatId = id,
                        sender = TdApi.MessageSenderUser(id),
                        content = TdApiFixtures.messageText("Hello from ${titles.getValue(id)}"),
                    ),
                positions = positions.toTypedArray(),
                unreadCount = unreadCount,
            )
        }

        private fun position(
            folder: Int,
            order: Long,
        ) = TdApiFixtures.chatPosition(if (folder == ARCHIVE) TdApi.ChatListArchive() else TdApi.ChatListMain(), order)

        private fun lastMessageId(chatId: Long) = chatId * 1_000
    }
}
