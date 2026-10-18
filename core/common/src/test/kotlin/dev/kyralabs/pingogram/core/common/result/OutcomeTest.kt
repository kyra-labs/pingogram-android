package dev.kyralabs.pingogram.core.common.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OutcomeTest {
    @Test
    fun `map transforms a success`() {
        assertEquals(Outcome.Success(4), Outcome.Success(2).map { it * 2 })
    }

    @Test
    fun `map keeps a failure`() {
        val failure: Outcome<Int> = Outcome.Failure(DomainError.Network)

        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `onSuccess and onFailure run only for their case`() {
        val seen = mutableListOf<Any>()

        Outcome.Success(1).onSuccess { seen.add(it) }.onFailure { seen.add(it) }
        Outcome.Failure(DomainError.NotFound).onSuccess { seen.add(it) }.onFailure { seen.add(it) }

        assertEquals(listOf(1, DomainError.NotFound), seen)
    }

    @Test
    fun `getOrNull returns the value of a success only`() {
        assertEquals("a", Outcome.Success("a").getOrNull())
        assertNull(Outcome.Failure(DomainError.Network).getOrNull())
    }
}
