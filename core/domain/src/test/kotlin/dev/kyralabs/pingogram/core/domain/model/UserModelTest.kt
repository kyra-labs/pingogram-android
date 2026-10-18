package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class UserModelTest {
    @Test
    fun `an online status holds until it expires`() {
        val status = UserStatus.Online(expiresAtEpochSeconds = 1_000)

        assertEquals(listOf(true, false, false), listOf(999L, 1_000L, 1_001L).map(status::isOnlineAt))
    }

    @Test
    fun `other statuses are never online`() {
        val statuses =
            listOf(
                UserStatus.Empty,
                UserStatus.Offline(wasOnlineAtEpochSeconds = 999),
                UserStatus.Recently,
                UserStatus.LastWeek,
                UserStatus.LastMonth,
            )

        assertFalse(statuses.any { it.isOnlineAt(nowEpochSeconds = 1_000) })
    }

    @Test
    fun `toString leaves names, usernames and the phone number out`() {
        val user =
            User(
                id = 7,
                firstName = "Ada",
                lastName = "Lovelace",
                usernames = persistentListOf("ada"),
                phoneNumber = "9996621234",
                status = UserStatus.Recently,
                profilePhoto = null,
                isVerified = false,
                isScam = false,
                isFake = false,
                isPremium = false,
                isContact = true,
                isMutualContact = true,
                isSupport = false,
                type = UserType.Regular,
                haveAccess = true,
            )

        assertEquals("User(id=7, type=Regular, status=Recently, …)", user.toString())
    }
}
