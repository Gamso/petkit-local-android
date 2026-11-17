package org.petkitlocal.feature.settings.licences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.settings.OpenSourceComponent
import org.petkitlocal.core.domain.settings.OpenSourceProject
import org.petkitlocal.core.domain.settings.OpenSourceProjects
import org.petkitlocal.feature.settings.R

/** The callbacks of [LicencesScreen], one per user intent. */
internal class LicencesActions(
    val onBack: () -> Unit = {},
    val onProjectClick: (OpenSourceProject) -> Unit = {},
    val onOpen: (OpenSourceComponent) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@Composable
internal fun LicencesRoute(onBack: () -> Unit, viewModel: LicencesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LicencesScreen(
        uiState,
        LicencesActions(
            onBack = onBack,
            onProjectClick = viewModel::onProjectClick,
            onOpen = viewModel::onOpen,
            onDismiss = viewModel::onDismiss,
        ),
    )
}

/**
 * The open-source components the app ships, one card per project with its licences: a project of
 * one component opens its licence text, one of several ("N components") unfolds to list them with
 * their versions, each opening its own. Lazy, scrolling from the top.
 */
@Composable
internal fun LicencesScreen(uiState: LicencesUiState, actions: LicencesActions, modifier: Modifier = Modifier) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val gutter = PkTheme.spacing.gutter
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
        contentPadding = PaddingValues(
            start = insets.calculateStartPadding(direction) + gutter,
            end = insets.calculateEndPadding(direction) + gutter,
            top = insets.calculateTopPadding() + gutter,
            bottom = insets.calculateBottomPadding() + gutter,
        ),
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .testTag(LicencesTestTags.LIST),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m)) {
                PkBackButton(onClick = actions.onBack)
                Text(
                    stringResource(R.string.settings_licences_title),
                    style = PkTheme.typography.display,
                    color = PkTheme.colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.settings_licences_intro),
                    style = PkTheme.typography.bodyMedium,
                    color = PkTheme.colors.onSurfaceMuted,
                )
            }
        }
        val projects = uiState.projects
        if (projects == null) {
            item(key = "loading") { PkLoadingView(cards = 2) }
        } else {
            items(projects, key = { it.name }) { project ->
                ProjectCard(project, expanded = project.name in uiState.expanded, actions)
            }
        }
    }
    uiState.opened?.let { component ->
        LicenceDialog(component.name, component.licences, onDismiss = actions.onDismiss)
    }
}

@Composable
private fun ProjectCard(project: OpenSourceProject, expanded: Boolean, actions: LicencesActions) {
    val several = project.components.size > 1
    val state =
        stringResource(if (expanded) R.string.settings_licences_expanded else R.string.settings_licences_collapsed)
    PkCard(Modifier.fillMaxWidth().testTag(LicencesTestTags.project(project.name))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
                .clickable(role = Role.Button) { actions.onProjectClick(project) }
                .then(if (several) Modifier.semantics { stateDescription = state } else Modifier),
        ) {
            Column(Modifier.weight(1f)) {
                Text(projectName(project), style = PkTheme.typography.titleMedium, color = PkTheme.colors.onSurface)
                Text(subtitle(project), style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
            }
            Spacer(Modifier.width(PkTheme.spacing.s))
            Icon(
                when {
                    !several -> PkIcons.ChevronRight
                    expanded -> PkIcons.ExpandLess
                    else -> PkIcons.ExpandMore
                },
                contentDescription = null,
                tint = PkTheme.colors.onSurfaceMuted,
            )
        }
        if (several && expanded) {
            project.components.forEach { component ->
                HorizontalDivider(color = PkTheme.colors.outline)
                ComponentRow(component, onClick = { actions.onOpen(component) })
            }
        }
    }
}

/** The AndroidX umbrella is ours to name, in the phone's language; every other is the project's own. */
@Composable
private fun projectName(project: OpenSourceProject): String = when (project.name) {
    OpenSourceProjects.ANDROIDX_NAME -> stringResource(R.string.settings_licences_androidx)
    else -> project.name
}

/** Its licences, after its number of components when it has several. */
@Composable
private fun subtitle(project: OpenSourceProject): String {
    val licences = project.licenceNames.joinToString(", ")
    val count = project.components.size
    if (count == 1) return licences
    return pluralStringResource(R.plurals.settings_licences_components, count, count) + " · " + licences
}

@Composable
private fun ComponentRow(component: OpenSourceComponent, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = PkTheme.spacing.xxs)
            .testTag(LicencesTestTags.component(component.id)),
    ) {
        Text(
            component.name,
            style = PkTheme.typography.bodyLarge,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        component.version?.let {
            Spacer(Modifier.width(PkTheme.spacing.s))
            Text(
                it,
                style = PkTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = PkTheme.colors.onSurfaceMuted,
            )
        }
    }
}
