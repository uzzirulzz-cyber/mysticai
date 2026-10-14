package com.mysticai.android

import java.util.UUID

data class ReferenceFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class AgentResult(
    val status: String,
    val output: String,
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val html: String,
    val model: String,
    val createdAt: Long = System.currentTimeMillis(),
    val agents: Map<String, AgentResult>,
)

enum class AppTab(val title: String) {
    BUILD("Build"),
    PROJECTS("Projects"),
    PROFILE("Profile"),
}

data class MysticUiState(
    val prompt: String = "",
    val references: List<ReferenceFile> = emptyList(),
    val projects: List<Project> = emptyList(),
    val selectedProjectId: String? = null,
    val activeTab: AppTab = AppTab.BUILD,
    val isBuilding: Boolean = false,
    val error: String? = null,
)

val agentNames = listOf(
    "planner" to "Planner",
    "designer" to "Designer",
    "architect" to "Architect",
    "builder" to "Builder",
    "reviewer" to "Reviewer",
)
