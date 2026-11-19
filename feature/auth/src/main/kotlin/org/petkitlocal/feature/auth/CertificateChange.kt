package org.petkitlocal.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.AnswerCertificateChangeUseCase
import org.petkitlocal.core.domain.auth.CertificateProblem
import org.petkitlocal.core.domain.auth.ObserveCertificateChangeUseCase

/**
 * The signed-in server presented another certificate than the one the owner pinned (SPEC.md §8).
 * Every request to it is refused meanwhile, whichever screen made it, so the question is asked
 * over the whole app, once: trust the new certificate, or not now.
 */
@HiltViewModel
class CertificateChangeViewModel @Inject constructor(
    observeChange: ObserveCertificateChangeUseCase,
    private val answer: AnswerCertificateChangeUseCase,
) : ViewModel() {
    val change: StateFlow<CertificateProblem.Changed?> = observeChange()

    private val trusting = MutableStateFlow(false)

    /** While the new certificate is being pinned: its button waits. */
    val busy: StateFlow<Boolean> = trusting.asStateFlow()

    fun onTrust() {
        val change = change.value ?: return
        if (trusting.value) return
        trusting.value = true
        viewModelScope.launch {
            try {
                answer.trust(change)
            } finally {
                trusting.value = false
            }
        }
    }

    fun onNotNow() {
        change.value?.let(answer::dismiss)
    }
}

/** Asks about a changed certificate, over whatever screen is showing, while there is one to ask about. */
@Composable
fun CertificateChangeRoute(viewModel: CertificateChangeViewModel = hiltViewModel()) {
    val change by viewModel.change.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    change?.let {
        Dialog(onDismissRequest = viewModel::onNotNow) {
            CertificateChangeContent(it, busy = busy, onTrust = viewModel::onTrust, onNotNow = viewModel::onNotNow)
        }
    }
}

/** The dialog's content, without the window: what screenshot tests capture. */
@Composable
internal fun CertificateChangeContent(
    change: CertificateProblem.Changed,
    onTrust: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    Surface(
        shape = PkTheme.shapes.dialog,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        modifier = modifier.testTag(SignInTestTags.CERTIFICATE_CHANGE),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(PkTheme.spacing.xl),
        ) {
            Text(
                stringResource(R.string.auth_certificate_change_title),
                style = PkTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.auth_certificate_change_message, change.host),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
            )
            CertificateDetails(change, color = PkTheme.colors.onSurface)
            // Stacked: in French at a large font, the two labels side by side do not fit.
            Column(
                verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
                modifier = Modifier.fillMaxWidth().padding(top = PkTheme.spacing.s),
            ) {
                PkButton(
                    stringResource(R.string.auth_certificate_trust_new),
                    onClick = onTrust,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag(SignInTestTags.TRUST_CERTIFICATE),
                )
                PkButton(
                    stringResource(R.string.auth_certificate_not_now),
                    onClick = onNotNow,
                    style = PkButtonStyle.Text,
                    modifier = Modifier.fillMaxWidth().testTag(SignInTestTags.CERTIFICATE_NOT_NOW),
                )
            }
        }
    }
}
