package com.mysticai.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MysticViewModel : ViewModel() {
    private val api = MysticApi()
    private var store: ProjectStore? = null
    private val mutableState = MutableStateFlow(MysticUiState())
    val state: StateFlow<MysticUiState> = mutableState.asStateFlow()

    fun initialize(filesDirectory: File) {
        if (store != null) return
        store = ProjectStore(filesDirectory)
        viewModelScope.launch {
            try {
                val projects = withContext(Dispatchers.IO) { requireNotNull(store).load() }
                mutableState.update { it.copy(projects = projects, selectedProjectId = projects.firstOrNull()?.id) }
            } catch (exception: Exception) {
                mutableState.update { it.copy(error = exception.message ?: "Saved projects could not be opened.") }
            }
        }
    }

    fun setPrompt(prompt: String) {
        mutableState.update { it.copy(prompt = prompt, error = null) }
    }

    fun setTab(tab: AppTab) {
        mutableState.update { it.copy(activeTab = tab, error = null) }
    }

    fun selectProject(project: Project) {
        mutableState.update {
            it.copy(
                activeTab = AppTab.BUILD,
                selectedProjectId = project.id,
                prompt = project.prompt,
                error = null,
            )
        }
    }

    fun addReferences(files: List<ReferenceFile>) {
        mutableState.update { current ->
            val combined = current.references + files
            val totalBytes = combined.sumOf { it.bytes.size.toLong() }
            when {
                combined.size > MAX_FILES -> current.copy(error = "Choose up to five reference files per build.")
                totalBytes > MAX_FILE_BYTES -> current.copy(error = "Reference files must total 3 MB or less.")
                else -> current.copy(references = combined, error = null)
            }
        }
    }

    fun removeReference(index: Int) {
        mutableState.update { current ->
            current.copy(references = current.references.filterIndexed { itemIndex, _ -> itemIndex != index })
        }
    }

    fun reportError(message: String) {
        mutableState.update { it.copy(error = message) }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    fun build() {
        val current = mutableState.value
        if (current.isBuilding) return
        if (current.prompt.isBlank()) {
            mutableState.update { it.copy(error = "Describe the app you want to build first.") }
            return
        }

        val prompt = current.prompt.trim()
        val references = current.references.toList()
        mutableState.update { it.copy(isBuilding = true, error = null, references = emptyList()) }
        viewModelScope.launch {
            try {
                val project = withContext(Dispatchers.IO) { api.build(prompt, references) }
                val projects = (listOf(project) + mutableState.value.projects)
                    .distinctBy(Project::id)
                    .take(MAX_PROJECTS)
                mutableState.update {
                    it.copy(
                        projects = projects,
                        selectedProjectId = project.id,
                        activeTab = AppTab.BUILD,
                        isBuilding = false,
                        prompt = "",
                    )
                }
                try {
                    withContext(Dispatchers.IO) { requireNotNull(store).save(projects) }
                } catch (exception: Exception) {
                    mutableState.update {
                        it.copy(error = "Your app was built, but ${exception.message ?: "project history could not be saved."}")
                    }
                }
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        isBuilding = false,
                        references = references,
                        error = exception.message ?: "The build failed. Please try again.",
                    )
                }
            }
        }
    }

    private companion object {
        const val MAX_FILES = 5
        const val MAX_FILE_BYTES = 3 * 1024 * 1024
        const val MAX_PROJECTS = 8
    }
}
