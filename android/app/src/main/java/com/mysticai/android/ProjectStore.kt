package com.mysticai.android

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class ProjectStore(private val directory: File) {
    fun load(): List<Project> {
        val file = File(directory, HISTORY_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val saved = JSONArray(file.readText(Charsets.UTF_8))
            List(saved.length()) { index -> saved.getJSONObject(index).toProject() }
                .sortedByDescending(Project::createdAt)
                .take(MAX_PROJECTS)
        } catch (exception: Exception) {
            throw IOException("Saved projects could not be opened: ${exception.message}", exception)
        }
    }

    fun save(projects: List<Project>) {
        val target = File(directory, HISTORY_FILE)
        val temporary = File(directory, "$HISTORY_FILE.tmp")
        val content = JSONArray().apply {
            projects.take(MAX_PROJECTS).forEach { project ->
                put(
                    JSONObject()
                        .put("id", project.id)
                        .put("prompt", project.prompt)
                        .put("html", project.html)
                        .put("model", project.model)
                        .put("createdAt", project.createdAt)
                        .put("agents", JSONObject().apply {
                            project.agents.forEach { (key, agent) ->
                                put(
                                    key,
                                    JSONObject()
                                        .put("status", agent.status)
                                        .put("output", agent.output),
                                )
                            }
                        }),
                )
            }
        }
        try {
            temporary.writeText(content.toString(), Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                target.writeText(content.toString(), Charsets.UTF_8)
                temporary.delete()
            }
        } catch (exception: Exception) {
            temporary.delete()
            throw IOException("Projects could not be saved: ${exception.message}", exception)
        }
    }

    private fun JSONObject.toProject(): Project {
        val savedAgents = optJSONObject("agents") ?: JSONObject()
        return Project(
            id = getString("id"),
            prompt = getString("prompt"),
            html = getString("html"),
            model = optString("model", "Gemini"),
            createdAt = getLong("createdAt"),
            agents = agentNames.associate { (key, _) ->
                val agent = savedAgents.optJSONObject(key)
                key to AgentResult(
                    status = agent?.optString("status").orEmpty(),
                    output = agent?.optString("output").orEmpty(),
                )
            },
        )
    }

    private companion object {
        const val HISTORY_FILE = "mystic-projects.json"
        const val MAX_PROJECTS = 8
    }
}
