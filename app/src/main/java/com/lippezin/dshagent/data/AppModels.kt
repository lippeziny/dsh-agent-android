package com.lippezin.dshagent.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Um provedor = uma URL base + uma chave + vários modelos.
 * [model] é o modelo padrão (usado também no fallback); [models] é a lista que aparece no seletor.
 */
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val models: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    val allModels: List<String>
        get() = (models + model).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val defaultModel: String
        get() = model.trim().ifBlank { allModels.firstOrNull().orEmpty() }
}

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
    val createdAt: Long = System.currentTimeMillis(),
    val model: String = "",
    val tools: List<String> = emptyList()
)

data class ToolModules(
    val projectFiles: Boolean = true,
    val projectMemory: Boolean = true,
    val webSearch: Boolean = true,
    val shellShizuku: Boolean = false,
    val mediaAttachments: Boolean = true,
    /** Cofre Obsidian como memória de longo prazo (só tem efeito quando um cofre está definido). */
    val vaultMemory: Boolean = true,
    /** Ferramentas de servidores MCP HTTP (só tem efeito quando há servidores ativos). */
    val mcp: Boolean = true
)

/** Servidor MCP acessível por HTTP (Streamable HTTP), ex.: ai-memory em http://IP:49374/mcp. */
data class McpServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val token: String = "",
    val enabled: Boolean = true,
    val requireApproval: Boolean = false
)

data class ShellApproval(
    val command: String,
    val description: String = "O modelo solicitou executar este comando no shell do Android via Shizuku. Ele roda com as permissões concedidas ao Shizuku; leia antes de autorizar.",
    val title: String = "Autorizar comando shell?",
    val confirmLabel: String = "Executar"
)

object ProviderPresets {
    data class Preset(val label: String, val baseUrl: String, val suggestedModel: String)

    val all = listOf(
        Preset("CodeCraft API", "https://codecraftapi.com/v1", ""),
        Preset("Roteador local (HTTP)", "http://127.0.0.1:", ""),
        Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        Preset("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini"),
        Preset("OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324"),
        Preset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Preset("Together AI", "https://api.together.xyz/v1", "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo"),
        Preset("Gemini (OpenAI-compatible)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        Preset("Compatível personalizado", "https://", "")
    )
}

private val baseUrlRegex = Regex("^https?://[^\\s/:?#]+(:\\d{1,5})?(/[^\\s]*)?$", RegexOption.IGNORE_CASE)

/** Aceita http:// ou https:// com host e porta opcional (ex.: http://localhost:20128/v1). */
fun isValidBaseUrl(url: String): Boolean = baseUrlRegex.matches(url.trim())

fun isCleartextUrl(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

/** localhost, loopback, redes privadas (10.x, 172.16-31.x, 192.168.x), Tailscale (100.64/10), link-local e nomes .local/.lan. */
fun isLocalOrPrivateHost(url: String): Boolean {
    val host = url.trim().substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan")) return true
    val parts = host.split('.')
    if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        return a == 127 || a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31) ||
            (a == 100 && b in 64..127) || (a == 169 && b == 254)
    }
    return false
}

private fun stringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
}

fun providersToJson(items: List<ProviderConfig>): String = JSONArray().apply {
    items.forEach { p ->
        put(JSONObject().apply {
            put("id", p.id)
            put("label", p.label)
            put("baseUrl", p.baseUrl)
            put("apiKey", p.apiKey)
            put("model", p.model)
            put("models", JSONArray(p.allModels))
            put("enabled", p.enabled)
        })
    }
}.toString()

fun providersFromJson(raw: String?): List<ProviderConfig> = runCatching {
    val arr = JSONArray(raw ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val model = o.optString("model", "")
        ProviderConfig(
            id = o.optString("id", UUID.randomUUID().toString()),
            label = o.optString("label", "Provedor"),
            baseUrl = o.optString("baseUrl", ""),
            apiKey = o.optString("apiKey", ""),
            model = model,
            models = stringList(o.optJSONArray("models")).ifEmpty { listOfNotNull(model.takeIf { it.isNotBlank() }) },
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
    items.forEach { e ->
        put(JSONObject().apply {
            put("id", e.id)
            put("role", e.role)
            put("content", e.content)
            put("createdAt", e.createdAt)
            put("attachmentNames", JSONArray(e.attachmentNames))
            put("model", e.model)
            put("tools", JSONArray(e.tools))
        })
    }
}.toString()

fun entriesFromJson(raw: String?): List<ChatEntry> = runCatching {
    val arr = JSONArray(raw ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ChatEntry(
            id = o.optString("id", UUID.randomUUID().toString()),
            role = o.optString("role", "assistant"),
            content = o.optString("content", ""),
            attachmentNames = stringList(o.optJSONArray("attachmentNames")),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            model = o.optString("model", ""),
            tools = stringList(o.optJSONArray("tools"))
        )
    }
}.getOrDefault(emptyList())

fun modulesToJson(m: ToolModules) = JSONObject().apply {
    put("projectFiles", m.projectFiles)
    put("projectMemory", m.projectMemory)
    put("webSearch", m.webSearch)
    put("shellShizuku", m.shellShizuku)
    put("mediaAttachments", m.mediaAttachments)
    put("vaultMemory", m.vaultMemory)
    put("mcp", m.mcp)
}.toString()

fun modulesFromJson(raw: String?): ToolModules = runCatching {
    val o = JSONObject(raw ?: "{}")
    ToolModules(
        projectFiles = o.optBoolean("projectFiles", true),
        projectMemory = o.optBoolean("projectMemory", true),
        webSearch = o.optBoolean("webSearch", true),
        shellShizuku = o.optBoolean("shellShizuku", false),
        mediaAttachments = o.optBoolean("mediaAttachments", true),
        vaultMemory = o.optBoolean("vaultMemory", true),
        mcp = o.optBoolean("mcp", true)
    )
}.getOrDefault(ToolModules())

fun sessionsToJson(items: List<ChatSession>): String = JSONArray().apply {
    items.forEach { s ->
        put(JSONObject().apply {
            put("id", s.id)
            put("title", s.title)
            put("createdAt", s.createdAt)
        })
    }
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


fun mcpServersToJson(items: List<McpServer>): String = JSONArray().apply {
    items.forEach { m ->
        put(JSONObject().apply {
            put("id", m.id)
            put("name", m.name)
            put("url", m.url)
            put("token", m.token)
            put("enabled", m.enabled)
            put("requireApproval", m.requireApproval)
        })
    }
}.toString()

fun mcpServersFromJson(raw: String?): List<McpServer> = runCatching {
    val arr = JSONArray(raw ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        McpServer(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Servidor MCP"),
            url = o.optString("url", ""),
            token = o.optString("token", ""),
            enabled = o.optBoolean("enabled", true),
            requireApproval = o.optBoolean("requireApproval", false)
        )
    }
}.getOrDefault(emptyList())
