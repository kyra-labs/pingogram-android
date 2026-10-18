package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationScopeTest {
    @Test
    fun `maps every notification scope`() {
        val scopes =
            listOf(
                TdApi.NotificationSettingsScopePrivateChats(),
                TdApi.NotificationSettingsScopeGroupChats(),
                TdApi.NotificationSettingsScopeChannelChats(),
            ).map { it.toNotificationScope() }

        assertEquals(NotificationScope.entries, scopes)
    }

    @Test
    fun `a scope is muted while its mute time is positive`() {
        assertEquals(
            listOf(true, false),
            listOf(3_600, 0).map { TdApiFixtures.scopeNotificationSettings(muteFor = it).isMuted },
        )
    }

    @Test
    fun `muting gives a chat a mute of its own that TDLib takes as forever`() {
        val settings = TdApiFixtures.chatNotificationSettings(useDefaultMuteFor = true).withOwnMute(isMuted = true)

        assertEquals(false, settings.useDefaultMuteFor)
        // TDLib: more than 366 days is forever.
        assertTrue(settings.muteFor > 366 * 86_400)
        assertEquals(true, settings.ownMute)
    }

    @Test
    fun `unmuting gives a chat a mute of 0 of its own, which a muted default does not override`() {
        val settings =
            TdApiFixtures.chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600).withOwnMute(false)

        assertEquals(false to 0, settings.useDefaultMuteFor to settings.muteFor)
        assertEquals(false, settings.ownMute)
    }

    @Test
    fun `changing the mute keeps every other setting`() {
        // Every other field differs from its default, so a field that is not copied shows.
        val custom =
            TdApi.ChatNotificationSettings(
                true,
                0,
                false,
                7,
                false,
                false,
                false,
                true,
                false,
                8,
                false,
                false,
                false,
                true,
                false,
                true,
            )

        assertEquals(custom.otherThanMute(), custom.withOwnMute(isMuted = true).otherThanMute())
    }

    private fun TdApi.ChatNotificationSettings.otherThanMute(): List<Any> =
        listOf(
            useDefaultSound,
            soundId,
            useDefaultShowPreview,
            showPreview,
            useDefaultMuteStories,
            muteStories,
            useDefaultStorySound,
            storySoundId,
            useDefaultShowStoryPoster,
            showStoryPoster,
            useDefaultDisablePinnedMessageNotifications,
            disablePinnedMessageNotifications,
            useDefaultDisableMentionNotifications,
            disableMentionNotifications,
        )
}
