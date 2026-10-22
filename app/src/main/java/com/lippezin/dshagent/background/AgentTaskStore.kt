package com.lippezin.dshagent.background

import android.content.Context
import com.lippezin.dshagent.data.ChatEntry
import com.lippezin.dshagent.data.entriesFromJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private, durable task payload. Keeps large prompts/history out of WorkManager's 10 KB Data limit. */
data class AgentTaskPayload(
    val taskId: String,
    val projectId: String,
    val sessionId: String,
    val historyJson: String,
    val userEntryJson: String,
    val userText: String,
    val attachmentUris: List<String>,
    val selectedProviderId: String?,
    val selectedModel: String?,
    val thinkingLevel: String,
    val modulesJson: String
) {
    val history: List<ChatEntry> get() = entriesFromJson(historyJson)
    val userEntry: ChatEntry? get() = entriesFromJson(userEntryJson).firstOrNull()

    fun toJson(): String = JSONObject().apply {
        put("taskId", taskId)
        put("projectId", projectId)
        put("sessionId", sessionId)
        put("historyJson", historyJson)
        put("userEntryJson", userEntryJson)
        put("userText", userText)
        put("attachmentUris", JSONArray(attachmentUris))
        put("selectedProviderId", selectedProviderId ?: JSONObject.NULL)
        put("selectedModel", selectedModel ?: JSONObject.NULL)
        put("thinkingLevel", thinkingLevel)
        put("modulesJson", modulesJson)
    }.toString()

    companion object {
        fun fromJson(raw: String): AgentTaskPayload {
            val o = JSONObject(raw)
            val uris = o.optJSONArray("attachmentUris") ?: JSONArray()
            return AgentTaskPayload(
                taskId = o.getString("taskId"),
                projectId = o.getString("projectId"),
                sessionId = o.getString("sessionId"),
                historyJson = o.optString("historyJson", "[]"),
                userEntryJson = o.optString("userEntryJson", "[]"),
                userText = o.optString("userText", ""),
                attachmentUris = (0 until uris.length()).map { uris.optString(it) },
                selectedProviderId = o.optString("selectedProviderId").takeUnless { it.isBlank() || it == "null" },
                selectedModel = o.optString("selectedModel").takeUnless { it.isBlank() || it == "null" },
                thinkingLevel = o.optString("thinkingLevel", "High"),
                modulesJson = o.optString("modulesJson", "{}")
            )
        }
    }
}

class AgentTaskStore(context: Context) {
    private val folder = File(context.filesDir, "agent-task-queue")

    private fun file(taskId: String): File {
        require(taskId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "ID de tarefa inválido." }
        return File(folder, "$taskId.json")
    }

    @Synchronized
    fun save(payload: AgentTaskPayload) {
        folder.mkdirs()
        val target = file(payload.taskId)
        val temp = File(folder, "${payload.taskId}.tmp")
        temp.writeText(payload.toJson(), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(payload.toJson(), Charsets.UTF_8)
            temp.delete()
        }
    }

    @Synchronized
    fun load(taskId: String): AgentTaskPayload? = runCatching {
        val target = file(taskId)
        if (!target.isFile) null else AgentTaskPayload.fromJson(target.readText(Charsets.UTF_8))
    }.getOrNull()

    @Synchronized
    fun delete(taskId: String) { runCatching { file(taskId).delete() } }
}
