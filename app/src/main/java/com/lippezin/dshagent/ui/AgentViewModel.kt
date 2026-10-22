package com.lippezin.dshagent.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.lippezin.dshagent.background.AgentTaskStore
import com.lippezin.dshagent.background.AgentTaskPayload
import com.lippezin.dshagent.background.AgentTaskWorker
import com.lippezin.dshagent.ai.Attachments
import com.lippezin.dshagent.ai.McpClient
import com.lippezin.dshagent.data.AppPrefs
import com.lippezin.dshagent.data.ChatEntry
import com.lippezin.dshagent.data.ChatSession
import com.lippezin.dshagent.data.McpServer
import com.lippezin.dshagent.data.Project
import com.lippezin.dshagent.data.ProviderConfig
import com.lippezin.dshagent.data.ShellApproval
import com.lippezin.dshagent.data.ToolModules
import com.lippezin.dshagent.data.entriesToJson
import com.lippezin.dshagent.data.modulesToJson
import com.lippezin.dshagent.storage.FileEntry
import com.lippezin.dshagent.storage.ProjectStorage
import com.lippezin.dshagent.storage.VaultMemory
import com.lippezin.dshagent.tools.ShellRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    enum class Tab { CHAT, PROJECTS, TOOLS, SETTINGS }

    private val prefs = AppPrefs(application)
    val storage = ProjectStorage(application, prefs)
    private val shellRunner = ShellRunner(application)

    var tab by mutableStateOf(Tab.CHAT)
    val projects = mutableStateListOf<Project>()
    val providers = mutableStateListOf<ProviderConfig>()
    val messages = mutableStateListOf<ChatEntry>()
    val sessions = mutableStateListOf<ChatSession>()
    val pendingAttachments = mutableStateListOf<Uri>()

    var activeSessionId by mutableStateOf("")
        private set
    var activeProjectId by mutableStateOf("")
        private set
    var inputText by mutableStateOf("")
    var selectedProviderId by mutableStateOf<String?>(null)
        private set
    var selectedModel by mutableStateOf<String?>(null)
        private set
    var thinkingLevel by mutableStateOf("High")
        private set
    var customInstructions by mutableStateOf("")
        private set
    var modules by mutableStateOf(ToolModules())
    /** Servidores MCP (HTTP) configurados e projeto usado como cofre Obsidian de memória. */
    val mcpServers = mutableStateListOf<McpServer>()
    var vaultProjectId by mutableStateOf<String?>(null)
    var memoryBusy by mutableStateOf(false)
        private set
    var isSending by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("")
    var notice by mutableStateOf<String?>(null)
    var shellStatus by mutableStateOf("Verificando Shizuku…")
        private set
    var approval by mutableStateOf<ShellApproval?>(null)
        private set
    var workspaceLabel by mutableStateOf("Armazenamento interno do app")
        private set

    var fileEntries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var filesBusy by mutableStateOf(false)
        private set
    var openedFileName by mutableStateOf<String?>(null)
        private set
    var openedFileContent by mutableStateOf("")
        private set
    var openedFileReadOnly by mutableStateOf(false)
        private set

    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private val workManager = WorkManager.getInstance(application)
    private val workInfos = workManager.getWorkInfosForUniqueWorkLiveData(AgentTaskWorker.UNIQUE_WORK_NAME)
    private val taskStore = AgentTaskStore(application)
    private var activeTaskId: String? = null
    private var activeTaskProjectId: String? = null
    private var activeTaskSessionId: String? = null
    val agentActivity = mutableStateListOf<String>()
    private val workInfoObserver = Observer<List<WorkInfo>> { infos -> handleWorkInfos(infos) }

    init {
        projects += prefs.getProjects()
        if (projects.isEmpty()) {
            val initial = Project(name = "Meu projeto")
            projects += initial
            prefs.setProjects(projects.toList())
        }
        activeProjectId = prefs.getActiveProjectId()?.takeIf { id -> projects.any { it.id == id } } ?: projects.first().id
        prefs.setActiveProjectId(activeProjectId)
        providers += prefs.getProviders()
        if (prefs.providersUnreadable()) {
            notice = "Não foi possível ler as chaves salvas (Keystore do Android indisponível). Cadastre os provedores novamente."
        }
        selectedProviderId = prefs.getSelectedProviderId()?.takeIf { id -> providers.any { it.id == id && it.enabled } }
            ?: providers.firstOrNull { it.enabled }?.id
        selectedModel = prefs.getSelectedModel()
        thinkingLevel = prefs.getThinkingLevel()
        customInstructions = prefs.getCustomInstructions()
        modules = prefs.getModules()
        mcpServers += prefs.getMcpServers()
        vaultProjectId = prefs.getVaultProjectId()?.takeIf { id -> projects.any { it.id == id } }
        loadActiveChat()
        refreshShellStatus()
        refreshWorkspaceLabel()
        refreshProjectFiles()
        workInfos.observeForever(workInfoObserver)
    }

    val activeProject: Project get() = projects.firstOrNull { it.id == activeProjectId } ?: projects.first()
    val selectedProvider: ProviderConfig? get() = providers.firstOrNull { it.id == selectedProviderId && it.enabled }

    /** Modelo efetivamente em uso: o escolhido, se ainda existir no provedor, ou o padrão do provedor. */
    val activeModel: String?
        get() = selectedProvider?.let { p ->
            selectedModel?.takeIf { it in p.allModels } ?: p.defaultModel.ifBlank { null }
        }

    /** Texto para chips/menus: o modelo, ou o nome do provedor quando ele usa o modelo padrão do roteador. */
    val activeModelLabel: String
        get() = activeModel ?: selectedProvider?.let { "${it.label} · modelo padrão" } ?: "Escolher modelo"

    fun showNotice(message: String) { notice = message }
    fun clearNotice() { notice = null }

    // ---------- Anexos ----------

    fun addAttachment(uris: List<Uri>) {
        var rejected = false
        uris.forEach { uri ->
            if (pendingAttachments.contains(uri)) return@forEach
            if (pendingAttachments.size >= 8) {
                rejected = true
                return@forEach
            }
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pendingAttachments += uri
        }
        if (rejected) showNotice("O limite por mensagem é 8 anexos.")
    }

    fun removeAttachment(uri: Uri) { pendingAttachments.remove(uri) }

    // ---------- Envio ----------

    fun sendMessage() {
        if (isSending) return
        val prompt = inputText.trim()
        if (prompt.isBlank() && pendingAttachments.isEmpty()) return
        if (providers.none { it.enabled && it.baseUrl.isNotBlank() }) {
            showNotice("Configure ao menos um provedor (URL base) em Configurações → Provedores e chaves.")
            tab = Tab.SETTINGS
            return
        }
        val projectId = activeProjectId
        val sessionId = activeSessionId
        val history = messages.toList()
        val uris = pendingAttachments.toList()
        val ctx = getApplication<Application>()
        val labels = uris.map { Attachments.displayName(ctx, it) }
        val defaultPrompt = if (labels.any { it.endsWith(".zip", ignoreCase = true) })
            "Analise o projeto do ZIP anexado: explique a estrutura e aponte o que pode ser melhorado."
        else "Analise os anexos enviados."
        val userEntry = ChatEntry(role = "user", content = prompt.ifBlank { defaultPrompt }, attachmentNames = labels)
        val taskId = UUID.randomUUID().toString()
        val payload = AgentTaskPayload(
            taskId = taskId,
            projectId = projectId,
            sessionId = sessionId,
            historyJson = entriesToJson(history),
            userEntryJson = entriesToJson(listOf(userEntry)),
            userText = userEntry.content,
            attachmentUris = uris.map { it.toString() },
            selectedProviderId = selectedProviderId,
            selectedModel = activeModel,
            thinkingLevel = thinkingLevel,
            modulesJson = modulesToJson(modules)
        )

        try {
            // Keep the request in app-private storage; WorkManager Data has a small size limit.
            taskStore.save(payload)
            messages += userEntry
            persistChat()
            inputText = ""
            pendingAttachments.clear()
            activeTaskId = taskId
            activeTaskProjectId = projectId
            activeTaskSessionId = sessionId
            agentActivity.clear()
            agentActivity += "Tarefa salva na fila persistente."
            isSending = true
            statusText = "Preparando tarefa em segundo plano…"

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<AgentTaskWorker>()
                .setInputData(workDataOf(AgentTaskWorker.KEY_TASK_ID to taskId))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(AgentTaskWorker.TAG_WORK)
                .addTag("${AgentTaskWorker.TAG_WORK}:$taskId")
                .build()
            workManager.enqueueUniqueWork(AgentTaskWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        } catch (error: Throwable) {
            taskStore.delete(taskId)
            isSending = false
            statusText = ""
            showNotice("Não foi possível agendar a tarefa: ${error.message ?: "erro desconhecido"}")
        }
    }

    fun stopGeneration() {
        activeTaskId?.let { taskStore.delete(it) }
        workManager.cancelUniqueWork(AgentTaskWorker.UNIQUE_WORK_NAME)
        approvalDeferred?.complete(false)
        activeTaskId = null
        activeTaskProjectId = null
        activeTaskSessionId = null
        isSending = false
        statusText = ""
        showNotice("Pedido de interrupção enviado.")
    }

    private fun handleWorkInfos(infos: List<WorkInfo>?) {
        if (infos.isNullOrEmpty()) {
            if (activeTaskId == null) {
                isSending = false
                statusText = ""
            }
            return
        }
        val activeStates = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
        val activeInfo = infos.lastOrNull { it.state in activeStates }
        if (activeTaskId == null && activeInfo != null) {
            activeTaskId = activeInfo.tags.firstOrNull { it.startsWith("${AgentTaskWorker.TAG_WORK}:") }
                ?.substringAfter("${AgentTaskWorker.TAG_WORK}:")
            activeTaskId?.let { id ->
                taskStore.load(id)?.let { payload ->
                    activeTaskProjectId = payload.projectId
                    activeTaskSessionId = payload.sessionId
                }
            }
        }
        val taskId = activeTaskId ?: return
        val info = infos.firstOrNull { item ->
            item.tags.contains("${AgentTaskWorker.TAG_WORK}:$taskId") ||
                item.progress.getString("taskId") == taskId || item.outputData.getString("taskId") == taskId
        } ?: return

        val progress = info.progress
        activeTaskProjectId = progress.getString("projectId") ?: activeTaskProjectId ?: taskStore.load(taskId)?.projectId
        activeTaskSessionId = progress.getString("sessionId") ?: activeTaskSessionId ?: taskStore.load(taskId)?.sessionId
        val status = progress.getString("status")
        val eventJson = progress.getString("events")
        if (!eventJson.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(eventJson)
                val values = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                agentActivity.clear()
                agentActivity.addAll(values)
            }
        }
        if (info.state in activeStates) {
            isSending = true
            statusText = if (info.state == WorkInfo.State.RUNNING) {
                status ?: "Trabalhando em segundo plano…"
            } else {
                if (status?.contains("Sem internet", ignoreCase = true) == true) status
                else "Na fila; aguardando conexão ou retomada automática…"
            }
            return
        }

        val wasSending = isSending
        isSending = false
        statusText = ""
        val projectId = info.outputData.getString("projectId") ?: activeTaskProjectId
        val sessionId = info.outputData.getString("sessionId") ?: activeTaskSessionId
        if (projectId != null && sessionId != null && activeProjectId == projectId && activeSessionId == sessionId) {
            loadActiveChat()
            refreshProjectFiles()
        }
        if (wasSending) {
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val provider = info.outputData.getString("provider")
                    val model = info.outputData.getString("model")
                    showNotice(if (!provider.isNullOrBlank() && !model.isNullOrBlank()) "Tarefa concluída em segundo plano · $provider / $model" else "Tarefa concluída em segundo plano.")
                }
                WorkInfo.State.FAILED -> showNotice("A tarefa em segundo plano falhou. Veja a mensagem de erro no chat.")
                WorkInfo.State.CANCELLED -> showNotice("A tarefa foi cancelada.")
                else -> Unit
            }
        }
        activeTaskId = null
        activeTaskProjectId = null
        activeTaskSessionId = null
    }

    override fun onCleared() {
        workInfos.removeObserver(workInfoObserver)
        super.onCleared()
    }

    fun retryLast() {
        if (isSending) return
        val lastUser = messages.indexOfLast { it.role == "user" }
        if (lastUser < 0) return
        val entry = messages[lastUser]
        while (messages.size > lastUser) messages.removeAt(messages.lastIndex)
        persistChat()
        inputText = entry.content
        if (entry.attachmentNames.isNotEmpty()) {
            showNotice("Os anexos não são reenviados na nova tentativa; anexe-os de novo se precisar.")
        }
        sendMessage()
    }

    fun deleteMessage(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages.removeAt(index)
            persistChat()
        }
    }

    // ---------- Sessões ----------

    fun newSession() {
        prefs.createSession(activeProjectId)
        loadActiveChat()
        tab = Tab.CHAT
        notice = "Nova sessão no projeto ${activeProject.name}. A conversa anterior foi preservada."
    }

    fun selectSession(session: ChatSession) {
        prefs.selectSession(activeProjectId, session.id)
        loadActiveChat()
        tab = Tab.CHAT
    }

    fun renameSession(session: ChatSession, title: String) {
        prefs.renameSession(activeProjectId, session.id, title)
        sessions.clear()
        sessions.addAll(prefs.getSessions(activeProjectId))
    }

    fun deleteSession(session: ChatSession) {
        prefs.deleteSession(activeProjectId, session.id)
        loadActiveChat()
    }

    // ---------- Projetos ----------

    fun createProject(nameInput: String) {
        val name = nameInput.trim().take(70)
        if (name.isBlank()) return
        val project = Project(name = name)
        projects += project
        prefs.setProjects(projects.toList())
        activeProjectId = project.id
        prefs.setActiveProjectId(project.id)
        loadActiveChat()
        refreshProjectFiles()
        tab = Tab.CHAT
        showNotice("Projeto ‘$name’ criado.")
    }

    fun selectProject(project: Project) {
        activeProjectId = project.id
        prefs.setActiveProjectId(project.id)
        loadActiveChat()
        refreshProjectFiles()
        tab = Tab.CHAT
    }

    fun renameProject(project: Project, nameInput: String) {
        val name = nameInput.trim().take(70)
        if (name.isBlank()) return
        val index = projects.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            projects[index] = project.copy(name = name)
            prefs.setProjects(projects.toList())
        }
    }

    fun deleteProject(project: Project) {
        if (projects.size <= 1) {
            showNotice("Mantenha ao menos um projeto.")
            return
        }
        val wasActive = project.id == activeProjectId
        if (vaultProjectId == project.id) selectVaultProject(null)
        projects.removeAll { it.id == project.id }
        prefs.setProjects(projects.toList())
        prefs.clearProjectData(project.id)
        if (wasActive) {
            activeProjectId = projects.first().id
            prefs.setActiveProjectId(activeProjectId)
            loadActiveChat()
            refreshProjectFiles()
        }
        viewModelScope.launch { runCatching { storage.deleteProject(project.id) } }
        showNotice("Projeto ‘${project.name}’ excluído.")
    }

    // ---------- Configurações ----------

    fun updateThinkingLevel(value: String) {
        thinkingLevel = value
        prefs.setThinkingLevel(value)
    }

    fun updateCustomInstructions(value: String) {
        customInstructions = value.take(4000)
        prefs.setCustomInstructions(customInstructions)
    }

    fun updateModules(value: ToolModules) {
        modules = value
        prefs.setModules(value)
    }

    // ---------- Memória: cofre Obsidian e servidores MCP ----------

    fun saveMcpServer(server: McpServer) {
        val index = mcpServers.indexOfFirst { it.id == server.id }
        if (index >= 0) mcpServers[index] = server else mcpServers += server
        prefs.setMcpServers(mcpServers.toList())
    }

    fun deleteMcpServer(id: String) {
        mcpServers.removeAll { it.id == id }
        prefs.setMcpServers(mcpServers.toList())
    }

    fun testMcpServer(server: McpServer) {
        if (memoryBusy) return
        memoryBusy = true
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val tools = McpClient(server).listTools()
                    "Conectado a ${server.name}: ${tools.size} ferramenta(s)" +
                        if (tools.isNotEmpty()) " (${tools.take(6).joinToString { it.name }}${if (tools.size > 6) "…" else ""})" else ""
                } catch (e: Throwable) {
                    "Falha ao conectar a ${server.name}: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            memoryBusy = false
            showNotice(message)
        }
    }

    fun selectVaultProject(project: Project?) {
        vaultProjectId = project?.id
        prefs.setVaultProjectId(project?.id)
    }

    /** Cria um projeto "Cofre de memória" já com pastas, CLAUDE.md, AGENTS.md, HOME.md e templates. */
    fun createVault() {
        if (memoryBusy) return
        memoryBusy = true
        viewModelScope.launch {
            try {
                val project = Project(name = "Cofre de memória")
                projects += project
                prefs.setProjects(projects.toList())
                val report = VaultMemory(storage).scaffold(project.id)
                selectVaultProject(project)
                showNotice("Cofre ‘${project.name}’ criado. $report")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice("Não foi possível criar o cofre: ${e.message ?: "erro desconhecido"}")
            } finally {
                memoryBusy = false
            }
        }
    }

    /** Completa o que faltar na estrutura do cofre atual, sem sobrescrever nada. */
    fun scaffoldVault() {
        val id = vaultProjectId ?: return
        if (memoryBusy) return
        memoryBusy = true
        viewModelScope.launch {
            try {
                val vault = VaultMemory(storage)
                val report = vault.scaffold(id, vault.root(id))
                if (id == activeProjectId) refreshProjectFiles()
                showNotice(report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice("Não foi possível configurar o cofre: ${e.message ?: "erro desconhecido"}")
            } finally {
                memoryBusy = false
            }
        }
    }

    fun selectModel(providerId: String, model: String) {
        val provider = providers.firstOrNull { it.id == providerId && it.enabled } ?: return
        selectedProviderId = providerId
        selectedModel = model.takeIf { it in provider.allModels } ?: provider.defaultModel
        prefs.setSelectedProviderId(providerId)
        prefs.setSelectedModel(selectedModel)
    }

    fun saveProvider(provider: ProviderConfig) {
        val index = providers.indexOfFirst { it.id == provider.id }
        if (index >= 0) providers[index] = provider else providers += provider
        prefs.setProviders(providers.toList())
        if (provider.enabled && selectedProviderId == null) selectModel(provider.id, provider.defaultModel)
        if (selectedProviderId == provider.id && selectedModel !in provider.allModels) selectModel(provider.id, provider.defaultModel)
        showNotice(
            if (provider.allModels.isEmpty()) "Provedor ‘${provider.label}’ salvo sem modelo fixo: o roteador usará o modelo padrão dele."
            else "Provedor ‘${provider.label}’ salvo com ${provider.allModels.size} modelo(s). A chave é armazenada cifrada no dispositivo."
        )
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        val index = providers.indexOfFirst { it.id == id }
        if (index < 0) return
        providers[index] = providers[index].copy(enabled = enabled)
        prefs.setProviders(providers.toList())
        if (!enabled && selectedProviderId == id) pickFirstEnabledProvider()
    }

    fun deleteProvider(id: String) {
        providers.removeAll { it.id == id }
        prefs.setProviders(providers.toList())
        if (selectedProviderId == id) pickFirstEnabledProvider()
    }

    private fun pickFirstEnabledProvider() {
        val next = providers.firstOrNull { it.enabled }
        if (next != null) selectModel(next.id, next.defaultModel)
        else {
            selectedProviderId = null
            selectedModel = null
            prefs.setSelectedModel(null)
        }
    }

    fun setWorkspace(uri: Uri) {
        storage.persistWorkspacePermission(uri)
        refreshWorkspaceLabel()
        refreshProjectFiles()
        showNotice("Pasta de trabalho vinculada. Os arquivos ficam na subpasta DSH Projects (arquivos do armazenamento interno não são migrados).")
    }

    private fun refreshWorkspaceLabel() {
        workspaceLabel = prefs.getWorkspaceTreeUri()?.let { uri ->
            Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.replace('%', ' ')?.ifBlank { null } ?: "Pasta externa selecionada"
        } ?: "Armazenamento interno do app"
    }

    fun refreshShellStatus() { shellStatus = shellRunner.status() }

    // ---------- Arquivos do projeto ----------

    fun refreshProjectFiles() {
        val id = activeProjectId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                fileEntries = storage.listEntries(id)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Throwable) {
                fileEntries = emptyList()
            }
        }
    }

    fun openProjectFile(path: String) {
        val id = activeProjectId
        viewModelScope.launch {
            openedFileName = path
            openedFileContent = ""
            openedFileReadOnly = false
            try {
                openedFileContent = storage.readFile(id, path).take(300_000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                openedFileContent = "Não foi possível abrir: ${e.message ?: "erro desconhecido"}"
                openedFileReadOnly = true
            }
        }
    }

    fun closeProjectFile() {
        openedFileName = null
        openedFileContent = ""
        openedFileReadOnly = false
    }

    fun saveOpenedFile(content: String) {
        val path = openedFileName ?: return
        val id = activeProjectId
        viewModelScope.launch {
            try {
                storage.writeFile(id, path, content)
                openedFileContent = content
                showNotice("Arquivo salvo.")
                refreshProjectFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice("Erro ao salvar: ${e.message ?: "desconhecido"}")
            }
        }
    }

    fun createProjectFile(path: String) {
        val id = activeProjectId
        val clean = path.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            try {
                storage.writeFile(id, clean, "")
                refreshProjectFiles()
                openProjectFile(clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice("Não foi possível criar o arquivo: ${e.message ?: "desconhecido"}")
            }
        }
    }

    fun deleteProjectPath(path: String) {
        val id = activeProjectId
        viewModelScope.launch {
            try {
                storage.deletePath(id, path)
                if (openedFileName == path) closeProjectFile()
                refreshProjectFiles()
                showNotice("Excluído: $path")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice("Não foi possível excluir: ${e.message ?: "desconhecido"}")
            }
        }
    }

    fun importZipIntoProject(uri: Uri) {
        val id = activeProjectId
        val name = Attachments.displayName(getApplication(), uri)
        viewModelScope.launch {
            filesBusy = true
            try {
                val report = storage.processZip(id, uri, name, extract = true)
                showNotice("ZIP importado em ‘${report.folder}/’: ${report.files} arquivo(s).")
                refreshProjectFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice("Falha ao importar o ZIP: ${e.message ?: "desconhecido"}")
            } finally {
                filesBusy = false
            }
        }
    }

    fun exportProjectZip(uri: Uri) {
        val id = activeProjectId
        viewModelScope.launch {
            filesBusy = true
            try {
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Não foi possível criar o arquivo de destino.")
                val count = storage.exportZip(id, out)
                showNotice("ZIP exportado com $count arquivo(s).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice("Falha ao exportar: ${e.message ?: "desconhecido"}")
            } finally {
                filesBusy = false
            }
        }
    }

    // ---------- Shell ----------

    fun runManualShell(commandInput: String) {
        val command = commandInput.trim()
        if (command.isBlank()) return
        if (!modules.shellShizuku) {
            showNotice("Ative o módulo Shell via Shizuku na aba Ferramentas antes de executar comandos.")
            return
        }
        viewModelScope.launch {
            val approved = askForApproval(ShellApproval(command = command))
            if (!approved) {
                showNotice("Execução cancelada.")
                return@launch
            }
            isSending = true
            statusText = "Executando no shell…"
            try {
                val result = withContext(Dispatchers.IO) { shellRunner.execute(command) }
                messages += ChatEntry(role = "assistant", content = "Resultado do terminal:\n```text\n$result\n```")
                persistChat()
                tab = Tab.CHAT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showNotice(e.message ?: "Erro ao executar o comando.")
            } finally {
                isSending = false
                statusText = ""
            }
        }
    }

    fun requestShizukuPermission() {
        try {
            shellRunner.requestPermission()
            refreshShellStatus()
            showNotice("Pedido de permissão enviado ao Shizuku.")
        } catch (e: Throwable) {
            showNotice(e.message ?: "Não foi possível pedir a permissão Shizuku.")
        }
    }

    fun resolveShellApproval(approved: Boolean) {
        approvalDeferred?.complete(approved)
    }

    private suspend fun askForApproval(request: ShellApproval): Boolean = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred?.complete(false)
        approvalDeferred = deferred
        approval = request
        try {
            withTimeoutOrNull(120_000L) { deferred.await() } ?: false
        } finally {
            approval = null
            approvalDeferred = null
        }
    }

    // ---------- Conversa ----------

    private fun loadActiveChat() {
        sessions.clear()
        sessions.addAll(prefs.getSessions(activeProjectId))
        activeSessionId = prefs.getActiveSessionId(activeProjectId)
        messages.clear()
        messages.addAll(prefs.getChat(activeProjectId))
    }

    private fun persistChat() {
        prefs.setChat(activeProjectId, messages.toList())
        sessions.clear()
        sessions.addAll(prefs.getSessions(activeProjectId))
        activeSessionId = prefs.getActiveSessionId(activeProjectId)
    }
}
