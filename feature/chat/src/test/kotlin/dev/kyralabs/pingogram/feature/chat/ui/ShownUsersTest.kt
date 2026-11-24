package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The users a conversation maps its messages with ([shownUsers], `docs/PERFORMANCE.md`): someone else's going online
 * maps nothing again; the other side of a private chat, whose status the header shows, stays live.
 */
class ShownUsersTest {
    private val users = FakeUserRepository()

    @Test
    fun `in a group, a user going online emits nothing`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))

            shownUsers(flowOf(null), users).test {
                awaitItem()

                users.setUsers(user(ADA, firstName = "Ada", status = ONLINE), user(GRACE, firstName = "Grace"))

                expectNoEvents()
            }
        }

    @Test
    fun `in a private chat, the other side's status follows, and no one else's`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))

            shownUsers(flowOf(ADA), users).test {
                awaitItem()

                users.setUsers(user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace", status = ONLINE))
                expectNoEvents()

                users.setUsers(
                    user(ADA, firstName = "Ada", status = ONLINE),
                    user(GRACE, firstName = "Grace", status = ONLINE),
                )
                assertEquals(ONLINE, awaitItem()[ADA]?.status)
            }
        }

    @Test
    fun `a renamed user shows`() =
        runTest {
            users.setUsers(user(GRACE, firstName = "Grace"))

            shownUsers(flowOf(null), users).test {
                awaitItem()

                users.setUsers(user(GRACE, firstName = "Grace Hopper"))

                assertEquals("Grace Hopper", awaitItem()[GRACE]?.firstName)
            }
        }

    private companion object {
        val ONLINE = UserStatus.Online(expiresAtEpochSeconds = 1_000)
    }
}
