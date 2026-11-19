package org.petkitlocal.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import java.time.ZoneId
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.CertificateProblem
import org.petkitlocal.core.ui.formatDate

/* A refused server certificate, as the owner checks it before trusting it (SPEC.md §8). */

/** What went wrong, in one sentence, then what to do about it when the certificate can be trusted. */
@Composable
internal fun certificateMessage(problem: CertificateProblem): String = when (problem) {
    is CertificateProblem.Untrusted ->
        stringResource(R.string.auth_certificate_untrusted) + " " +
            stringResource(R.string.auth_certificate_untrusted_help)

    is CertificateProblem.Changed ->
        stringResource(R.string.auth_certificate_changed) + " " + stringResource(R.string.auth_certificate_changed_help)

    is CertificateProblem.WrongHost -> stringResource(R.string.auth_certificate_wrong_host, problem.host)

    is CertificateProblem.NotValidNow -> stringResource(R.string.auth_certificate_not_valid_now)
}

/** The button that trusts it: none for a certificate that cannot be trusted. */
@Composable
internal fun trustLabel(problem: CertificateProblem): String? = when (problem) {
    is CertificateProblem.Untrusted -> stringResource(R.string.auth_certificate_trust)
    is CertificateProblem.Changed -> stringResource(R.string.auth_certificate_trust_new)
    is CertificateProblem.WrongHost, is CertificateProblem.NotValidNow -> null
}

/**
 * The server, the certificate's subject and validity, and its SHA-256 fingerprint; for a changed
 * one, the fingerprint trusted until now under it, so the two can be compared.
 */
@Composable
internal fun CertificateDetails(
    problem: CertificateProblem,
    color: Color,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val certificate = problem.presented
    val locale = LocalConfiguration.current.locales[0]
    val day = { instant: Instant -> formatDate(instant.toJavaInstant().atZone(zone).toLocalDate(), locale) }
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().testTag(SignInTestTags.CERTIFICATE_DETAILS),
    ) {
        Detail(stringResource(R.string.auth_certificate_server), problem.host, color)
        Detail(stringResource(R.string.auth_certificate_subject), certificate.subject, color)
        Detail(
            stringResource(R.string.auth_certificate_validity),
            stringResource(
                R.string.auth_certificate_validity_range,
                day(certificate.validFrom),
                day(certificate.validUntil),
            ),
            color,
        )
        val changed = problem as? CertificateProblem.Changed
        Detail(
            stringResource(
                if (changed !=
                    null
                ) {
                    R.string.auth_certificate_new_fingerprint
                } else {
                    R.string.auth_certificate_fingerprint
                },
            ),
            certificate.sha256,
            color,
            fingerprint = true,
            modifier = Modifier.testTag(SignInTestTags.CERTIFICATE_FINGERPRINT),
        )
        if (changed != null) {
            Detail(
                stringResource(R.string.auth_certificate_pinned_fingerprint),
                changed.pinnedSha256,
                color,
                fingerprint = true,
                modifier = Modifier.testTag(SignInTestTags.CERTIFICATE_PINNED_FINGERPRINT),
            )
        }
    }
}

/** A label over its value; a fingerprint in a fixed-width face, so its pairs line up to compare. */
@Composable
private fun Detail(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    fingerprint: Boolean = false,
) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = PkTheme.typography.labelMedium, color = color)
        Text(
            if (fingerprint) breakableFingerprint(value) else value,
            style = if (fingerprint) {
                PkTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                PkTheme.typography.bodyMedium
            },
            color = color,
        )
    }
}

/** A fingerprint that wraps between its pairs, never inside one: a zero-width space after each colon. */
internal fun breakableFingerprint(sha256: String): String = sha256.replace(":", ":\u200B")
