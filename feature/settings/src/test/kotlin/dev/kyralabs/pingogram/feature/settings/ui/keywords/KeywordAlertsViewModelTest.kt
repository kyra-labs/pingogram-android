package dev.kyralabs.pingogram.feature.settings.ui.keywords

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import dev.kyralabs.pingogram.core.testing.FakeKeywordAlertsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Keyword alerts' page (board 30): words, suggestions, where to look, the folders' sheet, errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeywordAlertsViewModelTest {
    private val repository = FakeKeywordAlertsRepository()
    private val folders =
        MutableStateFlow<ImmutableList<ChatFolder>>(
            persistentListOf(
                ChatFolder(1, FormattedText("Work", persistentListOf()), "Work", null),
                ChatFolder(2, FormattedText("Family", persistentListOf()), "Custom", null),
            ),
        )
    private val me = EditableProfile(firstName = "Ada", lastName = "Lovelace", username = "ada", bio = "")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val hits = MutableStateFlow<List<KeywordHit>>(emptyList())

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): KeywordAlertsViewModel {
        val viewModel = KeywordAlertsViewModel(repository, folders, { me }, handle, hits)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: KeywordAlertsViewModel,
        vararg events: KeywordAlertsEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    @Test
    fun `suggests the account's name and username until kept`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(
                listOf("Ada", "@ada"),
                viewModel.uiState.value.suggestions
                    .map { it.word },
            )

            send(viewModel, KeywordAlertsEvent.Add("ADA"))

            assertEquals(
                listOf("@ada"),
                viewModel.uiState.value.suggestions
                    .map { it.word },
            )
            assertEquals(
                listOf("Work", "Family"),
                viewModel.uiState.value.folders
                    .map { it.title },
            )
        }

    @Test
    fun `counts each word's matches this week, in any letter case, and none for a word without`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, KeywordAlertsEvent.Add("Launch"))
            send(viewModel, KeywordAlertsEvent.Add("release"))
            hits.value =
                listOf(
                    KeywordHit(chatId = 1, messageId = 10, word = "launch", date = 1),
                    KeywordHit(chatId = 2, messageId = 20, word = "LAUNCH", date = 2),
                )
            runCurrent()

            assertEquals(mapOf("Launch" to 2, "release" to 0), viewModel.uiState.value.matchCounts)
        }

    @Test
    fun `adds and removes words, and says when one is added`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<KeywordAlertsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, KeywordAlertsEvent.Add(" launch "), KeywordAlertsEvent.Add("  "))
            send(viewModel, KeywordAlertsEvent.Add("0.9.4"), KeywordAlertsEvent.Remove("launch"))

            assertEquals(
                listOf("0.9.4"),
                viewModel.uiState.value.settings!!
                    .words,
            )
            assertEquals(listOf(KeywordAlertsEffect.Added, KeywordAlertsEffect.Added), effects)
        }

    @Test
    fun `chooses folders in the sheet, and none keeps where it looked`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, KeywordAlertsEvent.ChooseFolders, KeywordAlertsEvent.FoldersChosen)
            assertEquals(
                KeywordScope.AllChats,
                viewModel.uiState.value.settings!!
                    .where,
            )

            send(viewModel, KeywordAlertsEvent.ChooseFolders, KeywordAlertsEvent.ToggleFolder(2))
            assertEquals(persistentSetOf(2), viewModel.uiState.value.choosingFolders)
            send(viewModel, KeywordAlertsEvent.FoldersChosen)

            assertNull(viewModel.uiState.value.choosingFolders)
            assertEquals(
                KeywordScope.Folders(persistentSetOf(2)),
                viewModel.uiState.value.settings!!
                    .where,
            )

            send(viewModel, KeywordAlertsEvent.ChooseFolders)
            assertEquals(persistentSetOf(2), viewModel.uiState.value.choosingFolders)
            send(viewModel, KeywordAlertsEvent.CloseFolders, KeywordAlertsEvent.Where(KeywordScope.MutedChats))
            assertEquals(
                KeywordScope.MutedChats,
                viewModel.uiState.value.settings!!
                    .where,
            )
        }

    @Test
    fun `the switches save, and a failed save says why`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<KeywordAlertsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, KeywordAlertsEvent.AlertWhenMuted(false), KeywordAlertsEvent.ShowInChatList(true))
            repository.failure = DomainError.Network
            send(viewModel, KeywordAlertsEvent.AlertWhenMuted(true))

            val settings = viewModel.uiState.value.settings!!
            assertEquals(false, settings.alertWhenMuted)
            assertEquals(true, settings.showInChatList)
            assertEquals(listOf(KeywordAlertsEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `the folders being chosen survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            send(viewModel(handle), KeywordAlertsEvent.ChooseFolders, KeywordAlertsEvent.ToggleFolder(1))

            assertEquals(persistentSetOf(1), viewModel(handle).uiState.value.choosingFolders)
        }
}
