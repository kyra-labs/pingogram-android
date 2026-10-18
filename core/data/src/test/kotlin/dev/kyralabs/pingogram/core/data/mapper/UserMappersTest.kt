package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UserMappersTest {
    @Test
    fun `maps names, active usernames and the phone number`() {
        val user =
            TdApiFixtures
                .user(firstName = "Ada", lastName = "Lovelace", usernames = listOf("ada", "countess"))
                .toUser()

        assertEquals(
            listOf("Ada", "Lovelace", "9996621234"),
            listOf(user.firstName, user.lastName, user.phoneNumber),
        )
        assertEquals(listOf("ada", "countess"), user.usernames)
    }

    @Test
    fun `missing names, usernames and phone number become empty`() {
        val tdUser =
            TdApiFixtures.user().apply {
                lastName = null
                usernames = null
                phoneNumber = null
            }

        val user = tdUser.toUser()

        assertEquals(listOf("", ""), listOf(user.lastName, user.phoneNumber))
        assertEquals(emptyList<String>(), user.usernames)
    }

    @Test
    fun `maps the verification status to badges`() {
        val user =
            TdApiFixtures
                .user(verificationStatus = TdApi.VerificationStatus(true, true, true, 0))
                .toUser()

        assertEquals(listOf(true, true, true), listOf(user.isVerified, user.isScam, user.isFake))
    }

    @Test
    fun `a user without a verification status has no badges`() {
        val user = TdApiFixtures.user(verificationStatus = null).toUser()

        assertEquals(listOf(false, false, false), listOf(user.isVerified, user.isScam, user.isFake))
    }

    @Test
    fun `maps the contact, premium, support and access flags`() {
        val user =
            TdApiFixtures
                .user(isPremium = true, isContact = true, isMutualContact = true, isSupport = true, haveAccess = false)
                .toUser()

        assertEquals(
            listOf(true, true, true, true, false),
            listOf(user.isPremium, user.isContact, user.isMutualContact, user.isSupport, user.haveAccess),
        )
    }

    @Test
    fun `maps the profile photo's files and minithumbnail`() {
        val photo =
            TdApiFixtures.profilePhoto(
                smallFileId = 10,
                bigFileId = 11,
                minithumbnail = TdApiFixtures.minithumbnail(data = byteArrayOf(4, 2)),
            )

        assertEquals(
            ProfilePhoto(FileRef(10, 1_024), FileRef(11, 1_024), Minithumbnail(40, 30, byteArrayOf(4, 2))),
            TdApiFixtures.user(profilePhoto = photo).toUser().profilePhoto,
        )
    }

    @Test
    fun `a user without a photo has none`() {
        assertNull(TdApiFixtures.user(profilePhoto = null).toUser().profilePhoto)
    }

    @Test
    fun `maps every user status`() {
        val statuses =
            listOf(
                TdApi.UserStatusEmpty(),
                TdApiFixtures.onlineStatus(expires = 1_000),
                TdApiFixtures.offlineStatus(wasOnline = 900),
                TdApi.UserStatusRecently(false),
                TdApi.UserStatusLastWeek(true),
                TdApi.UserStatusLastMonth(false),
                null,
            ).map { it.toUserStatus() }

        assertEquals(
            listOf(
                UserStatus.Empty,
                UserStatus.Online(expiresAtEpochSeconds = 1_000),
                UserStatus.Offline(wasOnlineAtEpochSeconds = 900),
                UserStatus.Recently,
                UserStatus.LastWeek,
                UserStatus.LastMonth,
                UserStatus.Empty,
            ),
            statuses,
        )
    }

    @Test
    fun `maps every user type`() {
        val types =
            listOf(
                TdApi.UserTypeRegular(),
                TdApi.UserTypeBot(),
                TdApi.UserTypeDeleted(),
                TdApi.UserTypeUnknown(),
            ).map { TdApiFixtures.user(type = it).toUser().type }

        assertEquals(listOf(UserType.Regular, UserType.Bot, UserType.Deleted, UserType.Unknown), types)
    }
}
