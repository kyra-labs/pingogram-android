package dev.kyralabs.pingogram.account

import dev.kyralabs.pingogram.core.domain.model.DeviceAccount
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.testing.FakeDeviceAccountsRepository
import kotlinx.collections.immutable.persistentListOf
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountMenuViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collected(viewModel: AccountMenuViewModel): AccountMenuViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `offers no menu while the app offers one account only`() =
        runTest {
            val viewModel = collected(AccountMenuViewModel(FakeDeviceAccountsRepository(isAvailable = false)))

            assertNull(viewModel.state.value)
        }

    @Test
    fun `lists every signed-in account with its unread chats, and Add account`() =
        runTest {
            val accounts = FakeDeviceAccountsRepository(accounts = persistentListOf(ada, grace))

            val viewModel = collected(AccountMenuViewModel(accounts))

            assertEquals(
                AccountMenuState(
                    accounts =
                        persistentListOf(
                            AccountMenuItem(0, 1, "Ada Lovelace", null, unreadChats = 0, isActive = true),
                            AccountMenuItem(1, 2, "Grace Hopper", null, unreadChats = 3, isActive = false),
                        ),
                    canAdd = true,
                ),
                viewModel.state.value,
            )
        }

    @Test
    fun `follows the accounts and the limit, and leaves out an account TDLib has not announced`() =
        runTest {
            val accounts = FakeDeviceAccountsRepository(accounts = persistentListOf(ada))
            val viewModel = collected(AccountMenuViewModel(accounts))

            accounts.accountList.value = persistentListOf(ada, grace.copy(user = null))
            accounts.canAdd.value = false
            runCurrent()

            assertEquals(
                listOf(0),
                viewModel.state.value
                    ?.accounts
                    ?.map { it.slot },
            )
            assertFalse(viewModel.state.value!!.canAdd)
        }

    @Test
    fun `switching and adding go to the device's accounts`() =
        runTest {
            val accounts = FakeDeviceAccountsRepository(accounts = persistentListOf(ada, grace))
            val viewModel = AccountMenuViewModel(accounts)

            viewModel.switchTo(1)
            viewModel.addAccount()

            assertEquals(listOf(1), accounts.switchedTo)
            assertEquals(1, accounts.adds)
        }

    private val ada = DeviceAccount(slot = 0, user = user(1, "Ada", "Lovelace"), unreadChats = 0, isActive = true)
    private val grace = DeviceAccount(slot = 1, user = user(2, "Grace", "Hopper"), unreadChats = 3, isActive = false)

    private fun user(
        id: Long,
        firstName: String,
        lastName: String,
    ) = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        usernames = persistentListOf(),
        phoneNumber = "",
        status = UserStatus.Empty,
        profilePhoto = null,
        isVerified = false,
        isScam = false,
        isFake = false,
        isPremium = false,
        isContact = false,
        isMutualContact = false,
        isSupport = false,
        type = UserType.Regular,
        haveAccess = true,
    )
}
