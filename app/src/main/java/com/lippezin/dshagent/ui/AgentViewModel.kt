package com.lippezin.dshagent.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lippezin.dshagent.ai.AgentEngine
import com.lippezin.dshagent.data.AppPrefs
import com.lippezin.dshagent.data.ChatEntry
import com.lippezin.dshagent.data.ChatSession
import com.lippezin.dshagent.data.Project
import com.lippezin.dshagent.data.ProviderConfig
import com.lippezin.dshagent.data.ShellApproval
import com.lippezin.dshagent.data.ToolModules
import com.lippezin.dshagent.storage.ProjectStorage
import com.lippezin.dshagent.tools.ShellRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    enum class Tab { CHAT, PROJECTS, TOOLS, SETTINGS }

    private val prefs = AppPrefs(application)
    val storage = ProjectStorage(application, prefs)
    private val shellRunner = ShellRunner(application)
    private val engine = AgentEngine(application, prefs, storage, shellRunner)

    var tab by mutableStateOf(Tab.CHAT)
    val projects = mutableStateListOf<Project>()
    val providers = mutableStateListOf<ProviderConfig>()
    val messages = mutableStateListOf<ChatEntry>()
    val sessions = mutableStateListOf<ChatSession>()
    var activeSessionId by mutableStateOf("")
        private set
    val pendingAttachments = mutableStateListOf<Uri>()

    var activeProjectId by mutableStateOf("")
        private set
    var inputText by mutableStateOf("")
    var selectedProviderId by mutableStateOf<String?>(null)
        private set
    var thinkingLevel by mutableStateOf("High")
        private set
    var modules by mutableStateOf(ToolModules())
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
    var projectFiles by mutableStateOf<List<String>>(emptyList())
        private set
    var openedFileName by mutableStateOf<String?>(null)
        private set
    var openedFileContent by mutableStateOf("")
        private set
    private var approvalDeferred: CompletableDeferred<Boolean>? = null

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
        selectedProviderId = prefs.getSelectedProviderId()?.takeIf { id -> providers.any { it.id == id && it.enabled } }
            ?: providers.firstOrNull { it.enabled }?.id
        thinkingLevel = prefs.getThinkingLevel()
        modules = prefs.getModules()
        loadActiveChat()
        refreshShellStatus()
        refreshWorkspaceLabel()
        refreshProjectFiles()
    }

    val activeProject: Project get() = projects.firstOrNull { it.id == activeProjectId } ?: projects.first()
    val selectedProvider: ProviderConfig? get() = providers.firstOrNull { it.id == selectedProviderId && it.enabled }

    fun showNotice(message: String) { notice = message }
    fun clearNotice() { notice = null }

    fun addAttachment(uris: List<Uri>) {
        uris.forEach { uri ->
            if (pendingAttachments.size < 8 && !pendingAttachments.contains(uri)) {
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                pendingAttachments += uri
            }
        }
        if (uris.size > 8) showNotice("O limite por mensagem é 8 anexos.")
    }

    fun removeAttachment(uri: Uri) { pendingAttachments.remove(uri) }

    fun sendMessage() {
        if (isSending) return
        val prompt = inputText.trim()
        if (prompt.isBlank() && pendingAttachments.isEmpty()) return
        val active = activeProjectId
        val history = messages.toList()
        val uris = pendingAttachments.toList()
        if (providers.none { it.enabled && it.apiKey.isNotBlank() && it.model.isNotBlank() }) {
            showNotice("Configure ao menos um provedor com chave de API e modelo em Configurações → Provedores e chaves.")
            tab = Tab.SETTINGS
            return
        }
        val labels = uris.map { uri -> uri.lastPathSegment?.substringAfterLast('/') ?: "anexo" }
        val userEntry = ChatEntry(role = "user", content = prompt.ifBlank { "Analise os anexos enviados." }, attachmentNames = labels)
        messages += userEntry
        persistChat()
        inputText = ""
        pendingAttachments.clear()
        isSending = true
        statusText = "Preparando tarefa…"
        viewModelScope.launch {
            try {
                val reply = engine.send(
                    projectId = active,
                    history = history,
                    userText = userEntry.content,
                    attachmentUris = uris,
                    selectedProviderId = selectedProviderId,
                    thinkingLevel = thinkingLevel,
                    modules = modules,
                    onStatus = { status -> statusText = status },
                    onShellApproval = { command -> askForShellApproval(command) }
                )
                val currentIndex = messages.indexOfFirst { it.id == userEntry.id }
                if (currentIndex >= 0 && reply.attachmentNames.isNotEmpty()) {
                    messages[currentIndex] = userEntry.copy(attachmentNames = reply.attachmentNames)
                }
                messages += ChatEntry(role = "assistant", content = reply.text)
                selectedProviderId = reply.providerId
                prefs.setSelectedProviderId(reply.providerId)
                persistChat()
                val toolSuffix = if (reply.toolLog.isNotEmpty()) " · ${reply.toolLog.size} ferramenta(s)" else ""
                showNotice("Resposta de ${reply.providerLabel}$toolSuffix")
            } catch (e: Throwable) {
                messages += ChatEntry(role = "assistant", content = "⚠️ ${e.message ?: "Falha ao executar a tarefa."}")
                persistChat()
            } finally {
                isSending = false
                statusText = ""
            }
        }
    }

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

    fun setThinkingLevel(value: String) {
        thinkingLevel = value
        prefs.setThinkingLevel(value)
    }

    fun setSelectedProvider(id: String) {
        if (providers.any { it.id == id && it.enabled }) {
            selectedProviderId = id
            prefs.setSelectedProviderId(id)
        }
    }

    fun saveProvider(provider: ProviderConfig) {
        val index = providers.indexOfFirst { it.id == provider.id }
        if (index >= 0) providers[index] = provider else providers += provider
        prefs.setProviders(providers.toList())
        if (provider.enabled && selectedProviderId == null) setSelectedProvider(provider.id)
        showNotice("Provedor ‘${provider.label}’ salvo. A chave é armazenada cifrada no dispositivo.")
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        val index = providers.indexOfFirst { it.id == id }
        if (index >= 0) {
            providers[index] = providers[index].copy(enabled = enabled)
            prefs.setProviders(providers.toList())
            if (!enabled && selectedProviderId == id) {
                selectedProviderId = providers.firstOrNull { it.enabled }?.id
                selectedProviderId?.let(prefs::setSelectedProviderId)
            }
        }
    }

    fun deleteProvider(id: String) {
        providers.removeAll { it.id == id }
        prefs.setProviders(providers.toList())
        if (selectedProviderId == id) {
            selectedProviderId = providers.firstOrNull { it.enabled }?.id
            selectedProviderId?.let(prefs::setSelectedProviderId)
        }
    }

    fun updateModules(value: ToolModules) {
        modules = value
        prefs.setModules(value)
    }

    fun setWorkspace(uri: Uri) {
        storage.persistWorkspacePermission(uri)
        refreshWorkspaceLabel()
        showNotice("Pasta de trabalho vinculada. Os arquivos ficam na subpasta DSH Projects.")
    }

    private fun refreshWorkspaceLabel() {
        workspaceLabel = prefs.getWorkspaceTreeUri()?.let { uri ->
            Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.replace('%', ' ')?.ifBlank { null } ?: "Pasta externa selecionada"
        } ?: "Armazenamento interno do app"
    }

    fun refreshShellStatus() { shellStatus = shellRunner.status() }

    fun refreshProjectFiles() {
        val id = activeProjectId
        if (id.isBlank()) return
        viewModelScope.launch {
            projectFiles = withContext(Dispatchers.IO) { storage.listFiles(id) }
        }
    }

    fun openProjectFile(path: String) {
        val id = activeProjectId
        viewModelScope.launch {
            openedFileName = path
            openedFileContent = withContext(Dispatchers.IO) {
                try { storage.readFile(id, path) } catch (e: Throwable) { "Erro ao abrir arquivo: ${e.message}" }
            }
        }
    }

    fun closeProjectFile() { openedFileName = null; openedFileContent = "" }

    fun runManualShell(commandInput: String) {
        val command = commandInput.trim()
        if (command.isBlank()) return
        if (!modules.shellShizuku) {
            showNotice("Ative o módulo Shell via Shizuku na aba Ferramentas antes de executar comandos.")
            return
        }
        viewModelScope.launch {
            val approved = askForShellApproval(command)
            if (!approved) {
                showNotice("Execução cancelada.")
                return@launch
            }
            isSending = true
            statusText = "Executando no shell…"
            try {
                val result = withContext(Dispatchers.IO) { shellRunner.execute(command) }
                messages += ChatEntry(role = "assistant", content = """Resultado do terminal:
```text
$result
```""")
                persistChat()
                tab = Tab.CHAT
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

    private suspend fun askForShellApproval(command: String): Boolean = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred?.complete(false)
        approvalDeferred = deferred
        approval = ShellApproval(command)
        val result = withTimeoutOrNull(120_000L) { deferred.await() } ?: false
        approval = null
        approvalDeferred = null
        result
    }

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
