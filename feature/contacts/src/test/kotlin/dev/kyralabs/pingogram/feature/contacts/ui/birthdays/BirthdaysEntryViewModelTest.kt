package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.CloseBirthday
import dev.kyralabs.pingogram.core.testing.FakeBirthdayRepository
import dev.kyralabs.pingogram.core.testing.FakeBirthdaysRowRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.contacts.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.contacts.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.contacts.ui.contact
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant

/**
 * [BirthdaysEntryViewModel] (`REQUIREMENTS.md` FR-X18): the Contacts tab's Birthdays row, its names nearest first and
 * the others counted, none without a named birthday, and none while the setting is off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BirthdaysEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val birthdays = FakeBirthdayRepository()
    private val row = FakeBirthdaysRowRepository()
    private val users =
        FakeUserRepository().apply {
            userMap.value =
                persistentMapOf(1L to contact(1, "Ada"), 2L to contact(2, "Grace"), 3L to contact(3, "Alan"))
        }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): BirthdaysEntryViewModel {
        val viewModel = BirthdaysEntryViewModel(birthdays, row, users, FakeWallClock(NOW))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.entry.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `names today's birthday first, then the upcoming ones, and counts the rest`() =
        runTest(dispatcher) {
            birthdays.announced.value =
                persistentListOf(
                    CloseBirthday(3, Birthdate(23, 11, null)),
                    CloseBirthday(2, Birthdate(25, 11, null)),
                    CloseBirthday(1, Birthdate(24, 11, 1990)),
                )

            assertEquals(
                BirthdaysEntry(persistentListOf(UiText.Plain("Ada"), UiText.Plain("Grace")), more = 1),
                viewModel().entry.value,
            )
        }

    @Test
    fun `no row without a birthday whose name is known`() =
        runTest(dispatcher) {
            birthdays.announced.value = persistentListOf(CloseBirthday(9, Birthdate(24, 11, null)))

            assertNull(viewModel().entry.value)
        }

    @Test
    fun `turning the setting off hides the row, and on shows it again`() =
        runTest(dispatcher) {
            birthdays.announced.value = persistentListOf(CloseBirthday(1, Birthdate(24, 11, null)))
            val viewModel = viewModel()

            row.setEnabled(false)
            runCurrent()
            assertNull(viewModel.entry.value)

            row.setEnabled(true)
            runCurrent()
            assertEquals(BirthdaysEntry(persistentListOf(UiText.Plain("Ada")), more = -1), viewModel.entry.value)
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-24T12:00:00Z")
    }
}
