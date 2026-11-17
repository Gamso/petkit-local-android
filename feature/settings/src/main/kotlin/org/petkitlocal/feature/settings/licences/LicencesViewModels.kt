package org.petkitlocal.feature.settings.licences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.settings.GetOpenSourceProjectsUseCase
import org.petkitlocal.core.domain.settings.OpenSourceComponent
import org.petkitlocal.core.domain.settings.OpenSourceProject

/** The licences screen: the projects (null while read from the APK), which are unfolded, which licence is open. */
data class LicencesUiState(
    val projects: List<OpenSourceProject>? = null,
    /** The projects of several components unfolded to list them, by name. */
    val expanded: Set<String> = emptySet(),
    /** The component whose licence text is shown in a dialog. */
    val opened: OpenSourceComponent? = null,
)

@HiltViewModel
class LicencesViewModel @Inject constructor(projects: GetOpenSourceProjectsUseCase) : ViewModel() {
    private val state = MutableStateFlow(LicencesUiState())
    val uiState: StateFlow<LicencesUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { state.update { it.copy(projects = projects()) } }
    }

    /** A project of one component opens its licence; one of several unfolds, or folds back. */
    fun onProjectClick(project: OpenSourceProject) {
        val single = project.components.singleOrNull()
        if (single != null) {
            onOpen(single)
        } else {
            state.update { it.copy(expanded = it.expanded.toggled(project.name)) }
        }
    }

    fun onOpen(component: OpenSourceComponent) = state.update { it.copy(opened = component) }

    fun onDismiss() = state.update { it.copy(opened = null) }
}

private fun Set<String>.toggled(name: String): Set<String> = if (name in this) this - name else this + name
