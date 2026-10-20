package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatMute
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatNotificationSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationSettingsMappersTest {
    @Test
    fun `every scope maps to TDLib's and back`() {
        assertEquals(NotificationScope.entries, NotificationScope.entries.map { it.toTdScope().toNotificationScope() })
    }

    @Test
    fun `a chat's mute is the default, unmuted, muted until a time, or forever past 366 days`() {
        val now = 1_000L

        assertEquals(
            listOf(ChatMute.Default, ChatMute.Unmuted, ChatMute.MutedUntil(now + 3_600), ChatMute.MutedForever),
            listOf(
                chatNotificationSettings(useDefaultMuteFor = true, muteFor = 3_600),
                chatNotificationSettings(useDefaultMuteFor = false, muteFor = 0),
                chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600),
                chatNotificationSettings(useDefaultMuteFor = false, muteFor = 366 * 86_400 + 1),
            ).map { it.toChatMute(now) },
        )
    }
}
