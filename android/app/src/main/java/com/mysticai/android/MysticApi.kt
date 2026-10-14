package com.mysticai.android

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MysticApi {
    fun build(prompt: String, references: List<ReferenceFile>): Project {
        val request = JSONObject()
            .put("prompt", prompt)
            .put("files", JSONArray().apply {
                references.forEach { file ->
                    put(
                        JSONObject()
                            .put("name", file.name)
                            .put("mimeType", file.mimeType)
                            .put("data", Base64.encodeToString(file.bytes, Base64.NO_WRAP)),
                    )
                }
            })

        val connection = (URL(BUILD_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 70_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val response = try {
                JSONObject(responseText)
            } catch (exception: Exception) {
                throw IOException("Mystic AI returned an unreadable response. Please try again.", exception)
            }

            if (status !in 200..299) {
                throw IOException(response.optString("error").ifBlank {
                    "The build request failed with status $status."
                })
            }

            val html = response.optString("html")
            if (!html.contains("<html", ignoreCase = true)) {
                throw IOException("The builder returned an incomplete app. Please try again.")
            }

            val agentObject = response.optJSONObject("agents") ?: JSONObject()
            val results = agentNames.associate { (key, _) ->
                val agent = agentObject.optJSONObject(key)
                key to AgentResult(
                    status = agent?.optString("status").orEmpty().ifBlank { "Complete" },
                    output = agent?.optString("output").orEmpty(),
                )
            }
            return Project(
                prompt = prompt,
                html = html,
                model = response.optString("model", "Gemini"),
                agents = results,
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BUILD_ENDPOINT = "https://mysticai-tawny.vercel.app/api/build"
    }
}
