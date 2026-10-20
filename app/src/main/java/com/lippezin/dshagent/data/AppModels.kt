package com.lippezin.dshagent.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = true
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Nova sessão",
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatEntry(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val attachmentNames: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ToolModules(
    val projectFiles: Boolean = true,
    val projectMemory: Boolean = true,
    val webSearch: Boolean = true,
    val shellShizuku: Boolean = false,
    val mediaAttachments: Boolean = true
)

data class ShellApproval(
    val command: String,
    val description: String = "O modelo solicitou executar este comando no shell do Android."
)

object ProviderPresets {
    data class Preset(val label: String, val baseUrl: String, val suggestedModel: String)
    val all = listOf(
        Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        Preset("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini"),
        Preset("OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324"),
        Preset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Preset("Together AI", "https://api.together.xyz/v1", "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo"),
        Preset("Gemini (OpenAI-compatible)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        Preset("Compatível personalizado", "https://seu-endpoint/v1", "modelo")
    )
}

fun providersToJson(items: List<ProviderConfig>): String = JSONArray().apply {
    items.forEach { p -> put(JSONObject().apply {
        put("id", p.id); put("label", p.label); put("baseUrl", p.baseUrl)
        put("apiKey", p.apiKey); put("model", p.model); put("enabled", p.enabled)
    }) }
}.toString()

fun providersFromJson(raw: String?): List<ProviderConfig> = runCatching {
    val arr = JSONArray(raw ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ProviderConfig(
            id = o.optString("id", UUID.randomUUID().toString()),
            label = o.optString("label", "Provedor"),
            baseUrl = o.optString("baseUrl", ""),
            apiKey = o.optString("apiKey", ""),
            model = o.optString("model", ""),
            enabled = o.optBoolean("enabled", true)
        )
    }
}.getOrDefault(emptyList())

fun projectsToJson(items: List<Project>): String = JSONArray().apply {
    items.forEach { p -> put(JSONObject().apply { put("id", p.id); put("name", p.name) }) }
}.toString()

fun projectsFromJson(raw: String?): List<Project> = runCatching {
    val arr = JSONArray(raw ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Project(o.optString("id", UUID.randomUUID().toString()), o.optString("name", "Projeto"))
    }
}.getOrDefault(emptyList())

fun entriesToJson(items: List<ChatEntry>): String = JSONArray().apply {
    items.forEach { e -> put(JSONObject().apply {
        put("id", e.id); put("role", e.role); put("content", e.content)
        put("createdAt", e.createdAt); put("attachmentNames", JSONArray(e.attachmentNames))
    }) }
}.toString()

fun entriesFromJson(raw: String?): List<ChatEntry> = runCatching {
    val arr = JSONArray(raw ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val files = o.optJSONArray("attachmentNames") ?: JSONArray()
        ChatEntry(
            id = o.optString("id", UUID.randomUUID().toString()),
            role = o.optString("role", "assistant"),
            content = o.optString("content", ""),
            attachmentNames = (0 until files.length()).map { files.optString(it) },
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}.getOrDefault(emptyList())

fun modulesToJson(m: ToolModules) = JSONObject().apply {
    put("projectFiles", m.projectFiles); put("projectMemory", m.projectMemory)
    put("webSearch", m.webSearch); put("shellShizuku", m.shellShizuku)
    put("mediaAttachments", m.mediaAttachments)
}.toString()

fun modulesFromJson(raw: String?): ToolModules = runCatching {
    val o = JSONObject(raw ?: "{}")
    ToolModules(
        projectFiles = o.optBoolean("projectFiles", true),
        projectMemory = o.optBoolean("projectMemory", true),
        webSearch = o.optBoolean("webSearch", true),
        shellShizuku = o.optBoolean("shellShizuku", false),
        mediaAttachments = o.optBoolean("mediaAttachments", true)
    )
}.getOrDefault(ToolModules())


fun sessionsToJson(items: List<ChatSession>): String = JSONArray().apply {
    items.forEach { s -> put(JSONObject().apply {
        put("id", s.id); put("title", s.title); put("createdAt", s.createdAt)
    }) }
}.toString()

fun sessionsFromJson(raw: String?): List<ChatSession> = runCatching {
    val arr = JSONArray(raw ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ChatSession(
            id = o.optString("id", UUID.randomUUID().toString()),
            title = o.optString("title", "Nova sessão"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}.getOrDefault(emptyList())
