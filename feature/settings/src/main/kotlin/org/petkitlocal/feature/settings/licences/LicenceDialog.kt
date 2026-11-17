package org.petkitlocal.feature.settings.licences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.settings.OpenSourceLicence
import org.petkitlocal.feature.settings.R

/** A licence text over the screen: [title] (a component, or the app), each licence's name and full text. */
@Composable
internal fun LicenceDialog(title: String, licences: List<OpenSourceLicence>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { LicenceDialogContent(title, licences, onDismiss) }
}

/** The dialog's card, outside any window: what screenshot tests and previews render. */
@Composable
internal fun LicenceDialogContent(
    title: String,
    licences: List<OpenSourceLicence>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = PkTheme.shapes.dialog,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        modifier = modifier.testTag(LicencesTestTags.DIALOG),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier.padding(PkTheme.spacing.l),
        ) {
            Text(title, style = PkTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Column(
                verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = TEXT_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                licences.forEach { licence ->
                    Text(licence.name, style = PkTheme.typography.titleMedium, color = PkTheme.colors.onSurface)
                    SelectionContainer {
                        Text(
                            licence.text?.trim() ?: stringResource(R.string.settings_licence_text_missing),
                            style = PkTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = PkTheme.colors.onSurfaceMuted,
                        )
                    }
                }
            }
            PkButton(
                stringResource(R.string.settings_licence_close),
                onClick = onDismiss,
                style = PkButtonStyle.Text,
                modifier = Modifier.align(Alignment.End).testTag(LicencesTestTags.CLOSE),
            )
        }
    }
}

private val TEXT_MAX_HEIGHT = 480.dp

/** Tags for UI tests and the end-to-end flows. */
object LicencesTestTags {
    const val LIST = "licences"
    const val DIALOG = "licence_dialog"
    const val CLOSE = "licence_close"

    fun project(name: String) = "licences_project_$name"

    fun component(id: String) = "licences_component_$id"
}
