package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptions
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.AccountUiModel
import dev.kyralabs.pingogram.feature.settings.ui.ActiveSessionsUiState
import dev.kyralabs.pingogram.feature.settings.ui.BlockedSenderItem
import dev.kyralabs.pingogram.feature.settings.ui.BlockedUsersUiState
import dev.kyralabs.pingogram.feature.settings.ui.DataStorageUiState
import dev.kyralabs.pingogram.feature.settings.ui.EditProfileUiState
import dev.kyralabs.pingogram.feature.settings.ui.LicenseItem
import dev.kyralabs.pingogram.feature.settings.ui.MoreBlockedSenders
import dev.kyralabs.pingogram.feature.settings.ui.OpenSourceLibraryItem
import dev.kyralabs.pingogram.feature.settings.ui.OpenSourceLibraryUiState
import dev.kyralabs.pingogram.feature.settings.ui.OpenSourceLicensesUiState
import dev.kyralabs.pingogram.feature.settings.ui.PrivacyRulesState
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityUiState
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySettingUiState
import dev.kyralabs.pingogram.feature.settings.ui.ProfileAccount
import dev.kyralabs.pingogram.feature.settings.ui.ProfileFields
import dev.kyralabs.pingogram.feature.settings.ui.SessionItem
import dev.kyralabs.pingogram.feature.settings.ui.SessionsState
import dev.kyralabs.pingogram.feature.settings.ui.SettingsUiState
import dev.kyralabs.pingogram.feature.settings.ui.StorageUsageState
import dev.kyralabs.pingogram.feature.settings.ui.TwoStepVerificationSummary
import dev.kyralabs.pingogram.feature.settings.ui.UsernameStatus
import dev.kyralabs.pingogram.feature.settings.ui.reflowLicenseText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/** Sample states of the settings screens, for previews and screenshot tests. */
internal object SettingsPreviewData {
    /** An account without a photo, so its placeholder shows (previews load no TDLib files). */
    val account =
        AccountUiModel(
            userId = 42,
            name = "Ada Lovelace",
            photo = null,
            phoneNumber = "+44 7911 123456",
            username = "ada",
        )

    /**
     * The account with a photo and Telegram Premium; the photo shows where Coil has a preview image (screenshot tests),
     * else its placeholder.
     */
    val premiumAccount =
        account.copy(
            photo = ProfilePhoto(small = FileRef(1, 0), big = FileRef(2, 0), minithumbnail = null),
            isPremium = true,
        )

    val content = SettingsUiState(account = account)

    val storage =
        DataStorageUiState(
            usage =
                StorageUsageState.Content(
                    StorageUsage(filesSize = 1_534_000_000, fileCount = 3_456, databaseSize = 85_200_000),
                ),
        )

    val storageError = DataStorageUiState(usage = StorageUsageState.Error(DomainError.Network))

    // Privacy and security

    private val contactsWithExceptions =
        PrivacyRules(
            PrivacyChoice.Contacts,
            PrivacyExceptions(allowedUserCount = 2, allowedChatCount = 1, restrictedUserCount = 3),
        )

    val privacySecurity =
        PrivacySecurityUiState.Content(
            rules =
                persistentMapOf(
                    PrivacySetting.PhoneNumber to PrivacyRules(PrivacyChoice.Contacts),
                    PrivacySetting.LastSeen to contactsWithExceptions,
                    PrivacySetting.ProfilePhoto to PrivacyRules(PrivacyChoice.Everybody),
                    PrivacySetting.ForwardedMessages to PrivacyRules(PrivacyChoice.Everybody),
                    PrivacySetting.Calls to PrivacyRules(PrivacyChoice.Nobody),
                    PrivacySetting.ChatInvites to PrivacyRules(PrivacyChoice.Contacts),
                    PrivacySetting.VoiceMessages to PrivacyRules(PrivacyChoice.Everybody),
                ),
            blockedCount = 3,
            sessionCount = 4,
            twoStepVerification =
                TwoStepVerificationSummary(UiText.resource(R.string.settings_two_step_verification_on_recovery_email)),
        )

    val privacySetting =
        PrivacySettingUiState(
            setting = PrivacySetting.LastSeen,
            rules = PrivacyRulesState.Loaded(contactsWithExceptions),
        )

    /** Voice and video messages for a user without Telegram Premium. */
    val voiceMessages =
        PrivacySettingUiState(
            setting = PrivacySetting.VoiceMessages,
            rules = PrivacyRulesState.Loaded(PrivacyRules(PrivacyChoice.Everybody)),
            premiumRequired = true,
        )

    val blockedUsers =
        BlockedUsersUiState.Content(
            items =
                persistentListOf(
                    BlockedSenderItem(
                        sender = MessageSender.User(7),
                        name = UiText.Plain("Grace Hopper"),
                        detail = UiText.resource(R.string.settings_blocked_username, "grace"),
                        photo = null,
                        avatarId = 7,
                    ),
                    BlockedSenderItem(
                        sender = MessageSender.User(8),
                        name = UiText.Plain("Alan Turing"),
                        detail = UiText.resource(R.string.settings_blocked_phone, "+447700900123"),
                        photo = null,
                        avatarId = 8,
                        isUnblocking = true,
                    ),
                    BlockedSenderItem(
                        sender = MessageSender.User(9),
                        name = UiText.resource(UiR.string.user_deleted_account),
                        detail = null,
                        photo = null,
                        avatarId = 9,
                        placeholderIcon = UiR.drawable.ic_person_filled,
                    ),
                    BlockedSenderItem(
                        sender = MessageSender.Chat(-100_123),
                        name = UiText.Plain("Spam Channel"),
                        detail = UiText.resource(R.string.settings_blocked_username, "spamchannel"),
                        photo = null,
                        avatarId = -100_123,
                    ),
                ),
            totalCount = 4,
            more = MoreBlockedSenders.None,
        )

    private val thisDevice =
        SessionItem(
            id = 1,
            device = SessionDevice.Phone,
            title = UiText.Plain("Google Pixel 8"),
            app = parts("Pingogram 0.3.0", "Android 15"),
            activity = UiText.resource(R.string.settings_session_parts, "Berlin, Germany", online()),
        )

    val sessions =
        ActiveSessionsUiState(
            sessions =
                SessionsState.Loaded(
                    current = thisDevice,
                    unconfirmed =
                        persistentListOf(
                            SessionItem(
                                id = 2,
                                device = SessionDevice.Browser,
                                title = UiText.Plain("Chrome 129"),
                                app = parts("Telegram Web A 10.9", "Windows 11"),
                                activity = parts("Lisbon, Portugal", "9:12 AM"),
                            ),
                        ),
                    others =
                        persistentListOf(
                            SessionItem(
                                id = 3,
                                device = SessionDevice.Desktop,
                                title = UiText.Plain("MacBook Pro"),
                                app = parts("Telegram Desktop 5.6", "macOS 15"),
                                activity = parts("Berlin, Germany", "Mon"),
                            ),
                            SessionItem(
                                id = 4,
                                device = SessionDevice.Tablet,
                                title = UiText.Plain("iPad Air"),
                                app = parts("Telegram iOS 11.2", "iPadOS 18"),
                                activity = parts("Munich, Germany", "Mar 17"),
                                isPasswordPending = true,
                            ),
                        ),
                    inactiveSessionTtlDays = 180,
                ),
        )

    val onlyThisDevice =
        ActiveSessionsUiState(
            sessions =
                SessionsState.Loaded(
                    current = thisDevice,
                    unconfirmed = persistentListOf(),
                    others = persistentListOf(),
                    inactiveSessionTtlDays = 180,
                ),
        )

    // Edit profile

    private val savedProfile =
        ProfileFields(firstName = "Ada", lastName = "Lovelace", username = "ada", bio = "Poetical scientist.")

    /** The profile as TDLib has it, nothing changed yet; the avatar shows its placeholder, as in [account]. */
    val editProfile =
        EditProfileUiState.Content(
            account = ProfileAccount(userId = account.userId, name = account.name, photo = null),
            saved = savedProfile,
        )

    /**
     * [editProfile] with a photo, so "Remove photo" shows. Previews and tests load no TDLib files, so the avatar keeps
     * its placeholder.
     */
    val editProfileWithPhoto =
        editProfile.copy(
            account =
                editProfile.account?.copy(
                    photo = ProfilePhoto(small = FileRef(1, 0), big = FileRef(2, 0), minithumbnail = null),
                ),
        )

    /** The profile with a changed bio and [username], which shows [usernameStatus]. */
    fun editedProfile(
        usernameStatus: UsernameStatus,
        username: String = "ada_lovelace",
    ) = editProfile.copy(
        fields = savedProfile.copy(username = username, bio = "Poetical scientist. First programmer."),
        usernameStatus = usernameStatus,
    )

    /** The first libraries of the list, in its order (by name). */
    val openSourceLicenses =
        OpenSourceLicensesUiState.Content(
            persistentListOf(
                OpenSourceLibraryItem("androidx.activity:activity", "Activity", "1.13.0", "Apache License 2.0"),
                OpenSourceLibraryItem(
                    "com.mikepenz:aboutlibraries-core",
                    "AboutLibraries Core Library",
                    "15.3.0",
                    "Apache License 2.0",
                ),
                OpenSourceLibraryItem(
                    "androidx.compose.material3:material3",
                    "Compose Material3 Components",
                    "1.5.0-beta01",
                    "Apache License 2.0",
                ),
                OpenSourceLibraryItem("google-sans-flex", "Google Sans Flex", null, "SIL Open Font License 1.1"),
                OpenSourceLibraryItem("material-symbols", "Material Symbols", null, "Apache License 2.0"),
                OpenSourceLibraryItem(
                    "androidx.datastore:datastore-preferences-external-protobuf",
                    "Preferences External Protobuf",
                    "1.2.1",
                    "BSD 3-Clause License",
                ),
                OpenSourceLibraryItem("org.drinkless:tdlib", "TDLib", "a94b83d67a", "Boost Software License 1.0"),
            ),
        )

    /** TDLib's page, with the Boost license reflowed as the view model does. */
    val openSourceLibrary =
        OpenSourceLibraryUiState.Content(
            id = "org.drinkless:tdlib",
            name = "TDLib",
            version = "a94b83d67a",
            isMavenArtifact = true,
            description = "The cross-platform library for building Telegram clients that Pingogram is built on.",
            developers = "Aliaksei Levin, Arseny Smirnov",
            website = "https://github.com/tdlib/td",
            licenses =
                persistentListOf(
                    LicenseItem(
                        id = "BSL-1.0",
                        name = "Boost Software License 1.0",
                        text = reflowLicenseText(BOOST_LICENSE),
                        url = "https://www.boost.org/LICENSE_1_0.txt",
                    ),
                ),
        )

    /** A library whose license the build has no text of: a note and the link instead. */
    val openSourceLibraryWithoutText =
        OpenSourceLibraryUiState.Content(
            id = "material-symbols",
            name = "Material Symbols",
            version = null,
            isMavenArtifact = false,
            description = null,
            developers = "Google",
            website = "https://fonts.google.com/icons",
            licenses =
                persistentListOf(
                    LicenseItem(
                        id = "Apache-2.0",
                        name = "Apache License 2.0",
                        text = null,
                        url = "https://www.apache.org/licenses/LICENSE-2.0",
                    ),
                ),
        )

    private fun parts(
        first: String,
        second: String,
    ) = UiText.resource(R.string.settings_session_parts, first, second)

    private fun online() = UiText.resource(R.string.settings_session_online)
}

/** The Boost Software License 1.0, as published (TDLib's `LICENSE_1_0.txt`). */
private val BOOST_LICENSE =
    """
    Boost Software License - Version 1.0 - August 17th, 2003

    Permission is hereby granted, free of charge, to any person or organization
    obtaining a copy of the software and accompanying documentation covered by
    this license (the "Software") to use, reproduce, display, distribute,
    execute, and transmit the Software, and to prepare derivative works of the
    Software, and to permit third-parties to whom the Software is furnished to
    do so, all subject to the following:

    The copyright notices in the Software and this entire statement, including
    the above license grant, this restriction and the following disclaimer,
    must be included in all copies of the Software, in whole or in part, and
    all derivative works of the Software, unless such copies or derivative
    works are solely in the form of machine-executable object code generated by
    a source language processor.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE, TITLE AND NON-INFRINGEMENT. IN NO EVENT
    SHALL THE COPYRIGHT HOLDERS OR ANYONE DISTRIBUTING THE SOFTWARE BE LIABLE
    FOR ANY DAMAGES OR OTHER LIABILITY, WHETHER IN CONTRACT, TORT OR OTHERWISE,
    ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
    """.trimIndent()
