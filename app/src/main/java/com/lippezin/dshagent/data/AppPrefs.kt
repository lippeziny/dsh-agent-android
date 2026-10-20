package com.lippezin.dshagent.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dsh_agent", Context.MODE_PRIVATE)
    private val keystoreName = "AndroidKeyStore"
    private val keyAlias = "dsh_agent_secure_values_v1"

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(keystoreName).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keystoreName)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decrypt(value: String): String? = runCatching {
        val split = value.split(":", limit = 2)
        require(split.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(split[0], Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(split[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun getProviders(): List<ProviderConfig> = providersFromJson(decrypt(prefs.getString("providers_encrypted", null) ?: ""))
    fun setProviders(values: List<ProviderConfig>) {
        prefs.edit().putString("providers_encrypted", encrypt(providersToJson(values))).apply()
    }

    fun getProjects(): List<Project> = projectsFromJson(prefs.getString("projects", null))
    fun setProjects(values: List<Project>) { prefs.edit().putString("projects", projectsToJson(values)).apply() }

    fun getActiveProjectId(): String? = prefs.getString("active_project_id", null)
    fun setActiveProjectId(id: String) { prefs.edit().putString("active_project_id", id).apply() }

    fun getSelectedProviderId(): String? = prefs.getString("selected_provider_id", null)
    fun setSelectedProviderId(id: String) { prefs.edit().putString("selected_provider_id", id).apply() }

    fun getThinkingLevel(): String = prefs.getString("thinking_level", "High") ?: "High"
    fun setThinkingLevel(value: String) { prefs.edit().putString("thinking_level", value).apply() }

    fun getModules(): ToolModules = modulesFromJson(prefs.getString("tool_modules", null))
    fun setModules(value: ToolModules) { prefs.edit().putString("tool_modules", modulesToJson(value)).apply() }

    fun getWorkspaceTreeUri(): String? = prefs.getString("workspace_tree_uri", null)
    fun setWorkspaceTreeUri(uri: String) { prefs.edit().putString("workspace_tree_uri", uri).apply() }

    fun getSessions(projectId: String): List<ChatSession> {
        val key = "sessions_$projectId"
        val existing = sessionsFromJson(prefs.getString(key, null))
        if (existing.isNotEmpty()) {
            val active = prefs.getString("active_session_$projectId", null)
            if (existing.none { it.id == active }) prefs.edit().putString("active_session_$projectId", existing.first().id).apply()
            return existing
        }
        val initial = ChatSession(title = "Sessão 1")
        prefs.edit().putString(key, sessionsToJson(listOf(initial))).putString("active_session_$projectId", initial.id).apply()
        return listOf(initial)
    }

    fun createSession(projectId: String): ChatSession {
        val current = getSessions(projectId)
        val session = ChatSession(title = "Sessão ${current.size + 1}")
        val updated = listOf(session) + current
        prefs.edit().putString("sessions_$projectId", sessionsToJson(updated)).putString("active_session_$projectId", session.id).apply()
        return session
    }

    fun selectSession(projectId: String, sessionId: String) {
        if (getSessions(projectId).any { it.id == sessionId }) prefs.edit().putString("active_session_$projectId", sessionId).apply()
    }

    fun getActiveSessionId(projectId: String): String {
        val sessions = getSessions(projectId)
        return prefs.getString("active_session_$projectId", sessions.first().id) ?: sessions.first().id
    }

    fun updateActiveSessionTitle(projectId: String, message: String) {
        val current = getSessions(projectId)
        val active = prefs.getString("active_session_$projectId", current.first().id)
        val session = current.firstOrNull { it.id == active } ?: return
        if (session.title.startsWith("Sessão ") || session.title == "Nova sessão") {
            val title = message.replace('\n', ' ').trim().take(36).ifBlank { session.title }
            val updated = current.map { if (it.id == active) it.copy(title = title) else it }
            prefs.edit().putString("sessions_$projectId", sessionsToJson(updated)).apply()
        }
    }

    private fun activeChatKey(projectId: String): String {
        return "chat_${projectId}_${getActiveSessionId(projectId)}"
    }

    fun getChat(projectId: String): List<ChatEntry> = entriesFromJson(prefs.getString(activeChatKey(projectId), null))
    fun setChat(projectId: String, entries: List<ChatEntry>) {
        val firstPrompt = entries.firstOrNull { it.role == "user" }?.content
        if (!firstPrompt.isNullOrBlank()) updateActiveSessionTitle(projectId, firstPrompt)
        prefs.edit().putString(activeChatKey(projectId), entriesToJson(entries)).apply()
    }
}
