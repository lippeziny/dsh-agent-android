package com.lippezin.dshagent.ai

import com.lippezin.dshagent.data.McpServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo do Model Context Protocol sobre HTTP (transporte "Streamable HTTP", modo sem sessão ou com sessão).
 * Serve para servidores como o ai-memory (http://IP:49374/mcp). Servidores só-stdio (npx, etc.) não são suportados no Android.
 */
class McpClient(private val server: McpServer) {

    data class Tool(val name: String, val description: String, val schema: JSONObject)

    private var sessionId: String? = null
    private var nextId = 1
    private var initialized = false

    private fun post(body: JSONObject, expectResponse: Boolean): JSONObject? {
        val conn = URL(server.url.trim()).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            if (server.token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${server.token.trim()}")
            sessionId?.let { conn.setRequestProperty("Mcp-Session-Id", it) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            conn.getHeaderField("Mcp-Session-Id")?.let { sessionId = it }
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val hint = if (status == 401 || status == 403) " (confira o token Bearer do servidor)" else ""
                throw IOException("${server.name}: HTTP $status$hint ${text.take(200)}")
            }
            if (!expectResponse || text.isBlank()) return null
            return parse(text, conn.contentType.orEmpty().lowercase(), body.optInt("id", -1))
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String, contentType: String, id: Int): JSONObject? {
        val trimmed = text.trimStart()
        if (contentType.contains("text/event-stream") || trimmed.startsWith("event:") || trimmed.startsWith("data:")) {
            var found: JSONObject? = null
            for (line in text.lineSequence()) {
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                if (obj.has("id") && obj.optInt("id", -2) == id) found = obj
            }
            return found
        }
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun rpc(method: String, params: JSONObject = JSONObject()): JSONObject {
        val id = nextId++
        val request = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
        val response = post(request, expectResponse = true)
            ?: throw IOException("${server.name}: resposta vazia ou inválida para $method")
        response.optJSONObject("error")?.let { error ->
            throw IOException("${server.name}: ${error.optString("message", "erro MCP")}")
        }
        return response.optJSONObject("result") ?: JSONObject()
    }

    private fun ensureInitialized() {
        if (initialized) return
        rpc(
            "initialize",
            JSONObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "DSH Agent").put("version", "0.5.0"))
        )
        post(JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized"), expectResponse = false)
        initialized = true
    }

    fun listTools(): List<Tool> {
        ensureInitialized()
        val tools = ArrayList<Tool>()
        var cursor: String? = null
        var pages = 0
        do {
            val params = JSONObject()
            if (cursor != null) params.put("cursor", cursor)
            val result = rpc("tools/list", params)
            val array = result.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until array.length()) {
                val t = array.optJSONObject(i) ?: continue
                val name = t.optString("name")
                if (name.isBlank()) continue
                tools += Tool(name, t.optString("description"), t.optJSONObject("inputSchema") ?: JSONObject().put("type", "object"))
            }
            cursor = result.optString("nextCursor").takeIf { it.isNotBlank() }
            pages++
        } while (cursor != null && pages < 10)
        return tools
    }

    fun callTool(name: String, arguments: JSONObject): String {
        ensureInitialized()
        val result = rpc("tools/call", JSONObject().put("name", name).put("arguments", arguments))
        val content = result.optJSONArray("content")
        val text = buildString {
            if (content != null) {
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    if (item.optString("type") == "text") append(item.optString("text")).append('\n')
                    else append("[conteúdo '").append(item.optString("type")).append("' omitido]\n")
                }
            }
            result.optJSONObject("structuredContent")?.let { if (isEmpty()) append(it.toString()) }
        }.trim()
        return if (result.optBoolean("isError", false)) "Erro do servidor MCP: $text" else text.ifBlank { "(sem conteúdo)" }
    }
}
