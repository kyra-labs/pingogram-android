package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.tdlib.TdError
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import kotlin.time.Duration.Companion.seconds

class TdApiFixturesTest {
    /** Guards TDLib bumps: a new state needs a fixture here and a mirror in core:tdlib's AuthState. */
    @Test
    fun `fixtures cover every AuthorizationState of the pinned TDLib version`() {
        assertEquals(
            concreteTdApiSubclassesOf(TdApi.AuthorizationState::class.java),
            TdApiFixtures.allAuthorizationStates().map { it.javaClass.simpleName }.toSet(),
        )
    }

    @Test
    fun `fixtures cover every ConnectionState of the pinned TDLib version`() {
        assertEquals(
            concreteTdApiSubclassesOf(TdApi.ConnectionState::class.java),
            TdApiFixtures.allConnectionStates().map { it.javaClass.simpleName }.toSet(),
        )
    }

    @Test
    fun `floodWait reads back as the given retry delay`() {
        val error = TdApiFixtures.floodWait(seconds = 30)

        assertEquals(30.seconds, TdError(error.code, error.message).retryAfter)
    }

    private fun concreteTdApiSubclassesOf(type: Class<*>): Set<String> =
        TdApi::class.java.declaredClasses
            .filter { type.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
            .map { it.simpleName }
            .toSet()
}
