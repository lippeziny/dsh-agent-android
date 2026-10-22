package com.lippezin.dshagent.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lippezin.dshagent.MainActivity
import com.lippezin.dshagent.ai.AgentEngine
import com.lippezin.dshagent.data.AppPrefs
import com.lippezin.dshagent.data.ChatEntry
import com.lippezin.dshagent.data.modulesFromJson
import com.lippezin.dshagent.storage.ProjectStorage
import com.lippezin.dshagent.tools.ShellRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException

/** Long-running WorkManager task. Its foreground notification lets work continue with the screen off. */
class AgentTaskWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val events = mutableListOf<String>()
    private var taskId: String = inputData.getString(KEY_TASK_ID).orEmpty()
    private var payload: AgentTaskPayload? = null
    private val notificationId = NOTIFICATION_BASE + (taskId.hashCode() and 0x3FFF)

    override suspend fun doWork(): Result {
        if (taskId.isBlank()) return Result.failure(workDataOf("error" to "ID da tarefa ausente."))
        val store = AgentTaskStore(applicationContext)
        payload = store.load(taskId)
        val task = payload ?: return Result.failure(workDataOf("taskId" to taskId, "error" to "Tarefa salva não encontrada."))
        try {
            ensureNotificationChannel()
            setForeground(foregroundInfo("Preparando a tarefa…"))
            publishStatus("Iniciando agente em segundo plano…")

            val prefs = AppPrefs(applicationContext)
            val storage = ProjectStorage(applicationContext, prefs)
            val engine = AgentEngine(applicationContext, prefs, storage, ShellRunner(applicationContext))
            val modules = modulesFromJson(task.modulesJson)

            val reply = engine.send(
                projectId = task.projectId,
                history = task.history,
                userText = task.userText,
                attachmentUris = task.attachmentUris.map { android.net.Uri.parse(it) },
                selectedProviderId = task.selectedProviderId,
                selectedModel = task.selectedModel,
                thinkingLevel = task.thinkingLevel,
                modules = modules,
                onStatus = { status -> publishStatus(status) },
                onApproval = { request ->
                    publishStatus("Ação sensível exige confirmação com o app aberto: ${request.title}. A ação foi recusada em segundo plano.")
                    false
                }
            )

            withContext(Dispatchers.IO) {
                val entries = prefs.getChat(task.projectId, task.sessionId).toMutableList()
                val userEntry = task.userEntry
                if (userEntry != null && entries.none { it.id == userEntry.id }) entries += userEntry
                val responseId = "background-task-$taskId"
                if (entries.none { it.id == responseId }) {
                    entries += ChatEntry(
                        id = responseId,
                        role = "assistant",
                        content = reply.text,
                        model = reply.model,
                        tools = reply.toolLog
                    )
                }
                prefs.setChat(task.projectId, task.sessionId, entries)
            }
            publishStatus("Concluído · ${reply.providerLabel} / ${reply.model}")
            store.delete(taskId)
            return Result.success(
                workDataOf(
                    "taskId" to taskId,
                    "projectId" to task.projectId,
                    "sessionId" to task.sessionId,
                    "provider" to reply.providerLabel,
                    "model" to reply.model
                )
            )
        } catch (cancelled: CancellationException) {
            // Keep the payload on OS interruption; WorkManager can restart the task after process death.
            throw cancelled
        } catch (error: Throwable) {
            if (error is IOException && !hasValidatedInternet()) {
                publishStatus("Sem internet. A tarefa ficará na fila e tentará novamente quando a conexão voltar.")
                return Result.retry()
            }
            val taskPayload = payload
            if (taskPayload != null) {
                runCatching {
                    val prefs = AppPrefs(applicationContext)
                    val entries = prefs.getChat(taskPayload.projectId, taskPayload.sessionId).toMutableList()
                    val userEntry = taskPayload.userEntry
                    if (userEntry != null && entries.none { it.id == userEntry.id }) entries += userEntry
                    val responseId = "background-task-$taskId"
                    if (entries.none { it.id == responseId }) {
                        entries += ChatEntry(
                            id = responseId,
                            role = "assistant",
                            content = "⚠️ A tarefa em segundo plano falhou: ${error.message ?: error.javaClass.simpleName}. Você pode tentar novamente quando a conexão estiver estável.",
                            model = ""
                        )
                    }
                    prefs.setChat(taskPayload.projectId, taskPayload.sessionId, entries)
                }
                store.delete(taskId)
            }
            return Result.failure(
                workDataOf(
                    "taskId" to taskId,
                    "projectId" to task.projectId,
                    "sessionId" to task.sessionId,
                    "error" to (error.message ?: error.javaClass.simpleName).take(500)
                )
            )
        }
    }

    private fun publishStatus(status: String) {
        if (status.isBlank()) return
        synchronized(events) {
            if (events.lastOrNull() != status) events += status
            while (events.size > 12) events.removeAt(0)
        }
        val eventJson = synchronized(events) { JSONArray(events).toString() }
        runCatching {
            runBlocking {
                setProgress(
                    workDataOf(
                        "taskId" to taskId,
                        "projectId" to payload?.projectId.orEmpty(),
                        "sessionId" to payload?.sessionId.orEmpty(),
                        "status" to status.take(350),
                        "events" to eventJson
                    )
                )
            }
        }
        runCatching {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, buildNotification(status))
        }
    }

    private fun hasValidatedInternet(): Boolean {
        return runCatching {
            val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork
            val caps = network?.let { manager.getNetworkCapabilities(it) }
            caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Tarefas do DSH Agent", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Mostra o progresso das tarefas de IA que continuam em segundo plano."
                    setShowBadge(false)
                }
            )
        }
    }

    private fun foregroundInfo(status: String): ForegroundInfo {
        val notification = buildNotification(status)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DSH Agent está trabalhando")
            .setContentText(status.take(160))
            .setStyle(NotificationCompat.BigTextStyle().bigText(status.take(350)))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val KEY_TASK_ID = "dsh_task_id"
        const val UNIQUE_WORK_NAME = "dsh-agent-current-task"
        const val TAG_WORK = "dsh-agent-task"
        const val CHANNEL_ID = "dsh_agent_background_tasks"
        private const val NOTIFICATION_BASE = 8200
    }
}
