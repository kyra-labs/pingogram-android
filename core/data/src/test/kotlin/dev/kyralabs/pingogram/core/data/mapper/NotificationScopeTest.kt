package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
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
}
