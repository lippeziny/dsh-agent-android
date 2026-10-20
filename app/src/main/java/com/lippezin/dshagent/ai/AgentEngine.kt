package com.lippezin.dshagent.ai

import android.content.Context
import com.lippezin.dshagent.data.AppPrefs
import com.lippezin.dshagent.data.ChatEntry
import com.lippezin.dshagent.data.ProviderConfig
import com.lippezin.dshagent.data.ToolModules
import com.lippezin.dshagent.storage.ProjectStorage
import com.lippezin.dshagent.tools.ShellRunner
import com.lippezin.dshagent.tools.WebSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AgentEngine(
    private val context: Context,
    private val prefs: AppPrefs,
    private val storage: ProjectStorage,
    private val shellRunner: ShellRunner
) {
    data class Reply(val text: String, val providerId: String, val providerLabel: String, val attachmentNames: List<String>, val toolLog: List<String>)

    private val mediaProcessor = MediaProcessor(context)
    private class ProviderException(val status: Int, val responseBody: String, val provider: ProviderConfig) : IOException(
        "${provider.label} (${status}): ${responseBody.take(350)}"
    )

    suspend fun send(
        projectId: String,
        history: List<ChatEntry>,
        userText: String,
        attachmentUris: List<android.net.Uri>,
        selectedProviderId: String?,
        thinkingLevel: String,
        modules: ToolModules,
        onStatus: (String) -> Unit,
        onShellApproval: suspend (String) -> Boolean
    ): Reply = withContext(Dispatchers.IO) {
        if (attachmentUris.isNotEmpty() && !modules.mediaAttachments) {
            throw IllegalStateException("O módulo de anexos multimídia está desativado. Ative-o em Ferramentas ou Configurações.")
        }
        val providers = prefs.getProviders().filter { it.enabled && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() && it.model.isNotBlank() }
        if (providers.isEmpty()) throw IllegalStateException("Nenhum provedor ativo com chave e modelo configurados. Abra Configurações → Provedores e chaves.")
        val ordered = providers.sortedBy { if (it.id == selectedProviderId) 0 else 1 }
        var activeProvider = ordered.first()
        val memory = if (modules.projectMemory) (try { storage.readMemory(projectId) } catch (_: Throwable) { "" }).take(12_000) else ""
        val sys = buildString {
            append("Você é DSH Agent, um agente de IA focado em concluir tarefas dentro do projeto ativo. Responda em português quando a pessoa escrever em português. Seja objetivo, confirme o que realmente fez e nunca afirme que uma ferramenta teve sucesso sem verificar o resultado.\n")
            append("Projeto ativo: $projectId.\n")
            append("Use ferramentas quando forem necessárias. Antes de executar qualquer comando shell, o app pedirá aprovação explícita ao usuário; não tente contornar essa confirmação. Não alegue ter acesso a recursos que a API ou o dispositivo não disponibilizaram.\n")
            append("Nível de esforço solicitado: $thinkingLevel. Pense com cuidado internamente, mas não revele cadeia de pensamento privada; forneça uma conclusão concisa com passos verificáveis.\n")
            if (modules.projectMemory && memory.isNotBlank()) append("\nMEMÓRIA PERSISTENTE DO PROJETO (informação de contexto, pode conter dados antigos; valide quando necessário):\n$memory\n")
            if (!modules.projectFiles) append("Ferramentas de leitura e escrita de arquivos estão desativadas.\n")
            if (!modules.webSearch) append("Pesquisa na web está desativada.\n")
            if (!modules.shellShizuku) append("Execução de shell está desativada.\n")
        }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        history.takeLast(30).forEach { entry ->
            if (entry.role == "user" || entry.role == "assistant") {
                val content = buildString {
                    append(entry.content)
                    if (entry.attachmentNames.isNotEmpty()) append("\n[Anexos anteriores: ${entry.attachmentNames.joinToString(", ")}]")
                }
                messages.put(JSONObject().put("role", entry.role).put("content", content))
            }
        }

        val attachmentNames: List<String>
        if (attachmentUris.isNotEmpty() && modules.mediaAttachments) {
            onStatus("Lendo anexos…")
            val pack = mediaProcessor.prepare(ordered, attachmentUris)
            attachmentNames = pack.names
            val firstText = "$userText${if (pack.extraText.isNotBlank()) "\n\nConteúdo extraído dos anexos:\n${pack.extraText}" else ""}"
            pack.content.put(0, JSONObject().put("type", "text").put("text", firstText))
            messages.put(JSONObject().put("role", "user").put("content", pack.content))
        } else {
            attachmentNames = emptyList()
            messages.put(JSONObject().put("role", "user").put("content", userText))
        }

        val enabledTools = buildTools(modules)
        val toolLog = mutableListOf<String>()
        repeat(7) { step ->
            onStatus("Consultando ${activeProvider.label} · etapa ${step + 1}")
            val responseAndProvider = postWithFallback(activeProvider, ordered, messages, enabledTools, thinkingLevel, onStatus)
            activeProvider = responseAndProvider.first
            val responseMessage = responseAndProvider.second.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw IOException("Resposta da API sem choices[0].message: ${responseAndProvider.second.toString().take(400)}")
            val toolCalls = responseMessage.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                val text = extractText(responseMessage.opt("content"))
                val finalText = text.ifBlank { "O modelo retornou uma resposta vazia. Confira o endpoint, o modelo e o formato de resposta do provedor." }
                return@withContext Reply(finalText, activeProvider.id, activeProvider.label, attachmentNames, toolLog)
            }
            if (step == 6) return@withContext Reply("Interrompi o ciclo após 7 etapas de ferramentas para evitar um loop infinito.\n\n${extractText(responseMessage.opt("content"))}", activeProvider.id, activeProvider.label, attachmentNames, toolLog)
            messages.put(JSONObject().put("role", "assistant").put("content", responseMessage.opt("content").takeUnless { it == JSONObject.NULL }).put("tool_calls", toolCalls))
            for (index in 0 until toolCalls.length()) {
                val call = toolCalls.getJSONObject(index)
                val callId = call.optString("id", "tool-$index")
                val function = call.optJSONObject("function") ?: JSONObject()
                val name = function.optString("name", "")
                val args = runCatching { JSONObject(function.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                onStatus("Ferramenta: $name")
                val output = try {
                    executeTool(name, args, projectId, modules, onShellApproval)
                } catch (e: Throwable) {
                    "Erro na ferramenta $name: ${e.message ?: "falha desconhecida"}"
                }
                toolLog += "$name: ${output.take(300).replace('\n', ' ')}"
                messages.put(JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", callId)
                    .put("name", name)
                    .put("content", output.take(12_000)))
            }
        }
        Reply("O agente atingiu o limite de etapas.", activeProvider.id, activeProvider.label, attachmentNames, toolLog)
    }

    private fun postWithFallback(
        preferred: ProviderConfig,
        all: List<ProviderConfig>,
        messages: JSONArray,
        tools: JSONArray,
        thinkingLevel: String,
        onStatus: (String) -> Unit
    ): Pair<ProviderConfig, JSONObject> {
        val ordered = listOf(preferred) + all.filter { it.id != preferred.id }
        val errors = mutableListOf<String>()
        ordered.forEachIndexed { index, provider ->
            try {
                val result = postChat(provider, messages, tools, thinkingLevel)
                if (index > 0) onStatus("Alternou para ${provider.label} após falha/cota do provedor anterior")
                return provider to result
            } catch (e: Throwable) {
                errors += "${provider.label}: ${e.message ?: e.javaClass.simpleName}"
                if (index < ordered.lastIndex) onStatus("${provider.label} falhou ou está sem cota; tentando o próximo provedor…")
            }
        }
        throw IOException("Todos os provedores falharam.\n${errors.joinToString("\n")}")
    }

    private fun postChat(provider: ProviderConfig, messages: JSONArray, tools: JSONArray, thinkingLevel: String): JSONObject {
        val endpoint = provider.baseUrl.trimEnd('/').let { base ->
            if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }
        val body = JSONObject()
            .put("model", provider.model)
            .put("messages", messages)
            .put("stream", false)
        if (tools.length() > 0) body.put("tools", tools).put("tool_choice", "auto")
        if (thinkingLevel != "Off" && isReasoningEffortCompatible(provider.model)) {
            body.put("reasoning_effort", if (thinkingLevel == "Max") "high" else thinkingLevel.lowercase())
        }
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 100_000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) {
            val lower = text.lowercase()
            val toolParamsUnsupported = status == 400 && tools.length() > 0 &&
                (lower.contains("tool_choice") || lower.contains("tool_calls") || lower.contains("tools are not supported") || lower.contains("does not support tools"))
            if (toolParamsUnsupported) return postChat(provider, messages, JSONArray(), thinkingLevel)
            throw ProviderException(status, text.ifBlank { "sem corpo de erro" }, provider)
        }
        return runCatching { JSONObject(text) }.getOrElse { throw IOException("JSON inválido de ${provider.label}: ${text.take(400)}") }
    }

    private fun isReasoningEffortCompatible(model: String): Boolean {
        val m = model.lowercase()
        return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("gpt-5")
    }

    private fun extractText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (i in 0 until value.length()) {
                val item = value.optJSONObject(i) ?: continue
                if (item.optString("type") == "text") append(item.optString("text"))
            }
        }
        else -> ""
    }

    private fun buildTools(m: ToolModules): JSONArray = JSONArray().apply {
        fun add(name: String, description: String, properties: JSONObject, required: List<String> = emptyList()) {
            put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", name).put("description", description)
                .put("parameters", JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray(required)))))
        }
        if (m.projectFiles) {
            add("list_project_files", "Lista os arquivos existentes no projeto ativo.", JSONObject())
            add("read_project_file", "Lê um arquivo de texto do projeto. Use caminho relativo, como src/Main.kt.", JSONObject().put("path", stringSchema("Caminho relativo do arquivo")), listOf("path"))
            add("write_project_file", "Cria ou substitui um arquivo de texto dentro do projeto ativo. Não use caminhos absolutos.", JSONObject()
                .put("path", stringSchema("Caminho relativo do arquivo"))
                .put("content", stringSchema("Conteúdo integral do arquivo")), listOf("path", "content"))
        }
        if (m.projectMemory) add("save_project_memory", "Salva uma memória durável e relevante no arquivo MEMORY.md do projeto ativo.", JSONObject().put("content", stringSchema("Fato durável, preferência do projeto ou decisão importante")), listOf("content"))
        if (m.webSearch) add("search_web", "Pesquisa na web e retorna títulos, URLs e resumos disponíveis.", JSONObject().put("query", stringSchema("Termos da pesquisa")), listOf("query"))
        if (m.shellShizuku) add("run_shell_command", "Executa um comando no shell Android via Shizuku. O usuário terá de aprovar cada comando. Não é root automaticamente.", JSONObject()
            .put("command", stringSchema("Comando sh -c a executar")), listOf("command"))
    }

    private fun stringSchema(description: String) = JSONObject().put("type", "string").put("description", description)

    private suspend fun executeTool(
        name: String,
        args: JSONObject,
        projectId: String,
        modules: ToolModules,
        onShellApproval: suspend (String) -> Boolean
    ): String = when (name) {
        "list_project_files" -> {
            check(modules.projectFiles) { "O módulo de arquivos está desativado." }
            storage.listFiles(projectId).ifEmpty { listOf("Projeto vazio.") }.joinToString("\n")
        }
        "read_project_file" -> {
            check(modules.projectFiles) { "O módulo de arquivos está desativado." }
            storage.readFile(projectId, args.getString("path"))
        }
        "write_project_file" -> {
            check(modules.projectFiles) { "O módulo de arquivos está desativado." }
            storage.writeFile(projectId, args.getString("path"), args.getString("content"))
        }
        "save_project_memory" -> {
            check(modules.projectMemory) { "O módulo de memória está desativado." }
            storage.saveMemory(projectId, args.getString("content"))
        }
        "search_web" -> {
            check(modules.webSearch) { "O módulo de pesquisa está desativado." }
            WebSearch.search(args.getString("query"))
        }
        "run_shell_command" -> {
            check(modules.shellShizuku) { "O módulo Shell/Shizuku está desativado." }
            val command = args.getString("command").take(4000)
            if (!onShellApproval(command)) "Execução cancelada pelo usuário." else shellRunner.execute(command)
        }
        else -> "Ferramenta desconhecida: $name"
    }
}
