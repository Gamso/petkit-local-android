package org.petkitlocal.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class OutcomeTest {
    @Test
    fun `map transforms a success`() {
        assertEquals(Outcome.Success(4), Outcome.Success(2).map { it * 2 })
    }

    @Test
    fun `map keeps a failure untouched`() {
        val failure = Outcome.Failure(DomainError.Network())
        assertSame(failure, failure.map { error("never called") })
    }

    @Test
    fun `onSuccess and onFailure run only on their own branch`() {
        val seen = mutableListOf<String>()
        Outcome.Success("a").onSuccess { seen += it }.onFailure { seen += "failure" }
        Outcome.Failure(DomainError.NotFound("device_not_found", "No device 4"))
            .onSuccess { seen += "success" }
            .onFailure { seen += (it as DomainError.NotFound).code }
        assertEquals(listOf("a", "device_not_found"), seen)
    }

    @Test
    fun `getOrNull is null on failure`() {
        assertEquals(1, Outcome.Success(1).getOrNull())
        assertNull(Outcome.Failure(DomainError.Network()).getOrNull())
    }
}
