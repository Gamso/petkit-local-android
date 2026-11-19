package org.petkitlocal.feature.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.auth.AnswerCertificateChangeUseCase
import org.petkitlocal.core.domain.auth.ObserveCertificateChangeUseCase
import org.petkitlocal.core.testing.FakeServerTrustRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class CertificateChangeViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeServerTrustRepository().apply {
        changedCertificate.value = FakeServerTrustRepository.CHANGED
    }

    private fun viewModel() = CertificateChangeViewModel(
        ObserveCertificateChangeUseCase(repository),
        AnswerCertificateChangeUseCase(repository),
    )

    @Test
    fun `trusting pins the new certificate and the question goes`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        assertEquals(FakeServerTrustRepository.CHANGED, viewModel.change.value)

        viewModel.onTrust()
        runCurrent()

        assertEquals(listOf(null to FakeServerTrustRepository.RENEWED), repository.trusted)
        assertNull(viewModel.change.value)
        assertFalse(viewModel.busy.value)
    }

    @Test
    fun `not now puts it off without trusting anything`() = runTest(main.dispatcher) {
        val viewModel = viewModel()

        viewModel.onNotNow()

        assertEquals(listOf(FakeServerTrustRepository.CHANGED), repository.dismissed)
        assertEquals(emptyList<Any>(), repository.trusted)
        assertNull(viewModel.change.value)
    }
}
