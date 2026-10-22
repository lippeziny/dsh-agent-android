package com.lippezin.dshagent.ai

import com.lippezin.dshagent.data.isValidBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lista os modelos disponíveis em uma base URL compatível com OpenAI (GET {base}/models). */
object ModelDiscovery {
    data class Result(val models: List<String>, val error: String? = null)

    private fun normalizeBase(baseUrl: String): String {
        var base = baseUrl.trim().trimEnd('/')
        for (suffix in listOf("/chat/completions", "/models")) {
            if (base.endsWith(suffix)) base = base.removeSuffix(suffix).trimEnd('/')
        }
        return base
    }

    suspend fun fetch(baseUrl: String, apiKey: String): Result = withContext(Dispatchers.IO) {
        val base = normalizeBase(baseUrl)
        if (!isValidBaseUrl(base)) return@withContext Result(emptyList(), "URL base inválida. Use http://host:porta/v1 (roteador local) ou https://…")
        val candidates = linkedSetOf("$base/models")
        if (!base.endsWith("/v1")) candidates += "$base/v1/models"
        var lastError = "Nenhum endpoint de modelos respondeu."
        for (url in candidates) {
            try {
                val (status, body) = get(url, apiKey)
                if (status in 200..299) {
                    val ids = parse(body)
                    if (ids.isNotEmpty()) return@withContext Result(ids)
                    lastError = "O endpoint respondeu, mas sem uma lista de modelos reconhecível."
                } else {
                    lastError = "HTTP $status em ${url.removePrefix("https://")}: ${body.take(160).replace('\n', ' ')}"
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        Result(emptyList(), lastError)
    }

    private fun get(url: String, apiKey: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return status to text
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<String> {
        val text = body.trim()
        val arr: JSONArray = runCatching {
            if (text.startsWith("[")) JSONArray(text) else {
                val obj = JSONObject(text)
                obj.optJSONArray("data") ?: obj.optJSONArray("models") ?: obj.optJSONArray("result") ?: JSONArray()
            }
        }.getOrDefault(JSONArray())
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            val id = when (item) {
                is String -> item
                is JSONObject -> item.optString("id").ifBlank { item.optString("name") }.ifBlank { item.optString("model") }
                else -> ""
            }
            val clean = id.removePrefix("models/").trim()
            if (clean.isNotEmpty()) out += clean
        }
        return out.toList().sortedBy { it.lowercase() }
    }
}
