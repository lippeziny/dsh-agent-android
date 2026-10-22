package com.lippezin.dshagent.tools

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

class ShellRunner(private val context: Context) {
    fun status(): String = try {
        when {
            !Shizuku.pingBinder() -> "Shizuku não está ativo"
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Permissão Shizuku ainda não concedida"
            else -> "Conectado (identidade ADB/shell; não é root automaticamente)"
        }
    } catch (ignored: Throwable) {
        "Shizuku indisponível"
    }

    fun requestPermission() {
        check(Shizuku.pingBinder()) { "Abra o Shizuku e inicie o serviço primeiro." }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(7421)
        }
    }

    private fun newShizukuProcess(command: String): Process {
        val commandArgs = arrayOf("sh", "-c", command)
        val signatures = listOf(
            arrayOf(Array<String>::class.java, Array<String>::class.java, String::class.java),
            arrayOf(Array<String>::class.java, java.util.Map::class.java, String::class.java),
            arrayOf(Array<String>::class.java, String::class.java),
        )

        for (signature in signatures) {
            try {
                val method = Shizuku::class.java.getMethod("newProcess", *signature)
                method.isAccessible = true
                return when (signature.size) {
                    3 -> method.invoke(null, commandArgs, null, null) as Process
                    2 -> method.invoke(null, commandArgs, null) as Process
                    else -> continue
                }
            } catch (_: NoSuchMethodException) {
                // A assinatura da API pode variar entre versões do Shizuku.
            } catch (_: IllegalAccessException) {
                // A API pode estar escondida ou protegida por módulos diferentes.
            } catch (_: ReflectiveOperationException) {
                // Cada assinatura é tentada em ordem de compatibilidade.
            }
        }

        throw IllegalStateException(
            "Shizuku.newProcess() não está disponível nesta versão da API. Verifique a dependência dev.rikka.shizuku:api."
        )
    }

    suspend fun execute(rawCommand: String): String = withContext(Dispatchers.IO) {
        check(Shizuku.pingBinder()) { "Shizuku não está ativo. Inicie o serviço e tente novamente." }
        check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            "O app ainda não recebeu a permissão Shizuku. Autorize nas configurações."
        }
        var command = rawCommand.trim()
        require(command.isNotBlank()) { "Comando vazio." }
        require(command.length <= 4_000) { "Comando muito longo." }
        // O comando é executado dentro do shell do dispositivo. O binário adb da máquina
        // não existe normalmente no Android; por conveniência, removemos o prefixo `adb shell`.
        if (command.startsWith("adb shell ", ignoreCase = true)) command = command.substringAfter("adb shell ").trim()
        else if (command.equals("adb shell", ignoreCase = true)) command = "id"
        else if (command.startsWith("adb ", ignoreCase = true)) {
            throw IllegalArgumentException("Este app executa comandos no shell do próprio Android. Para comandos locais, use `adb shell <comando>`; `adb install`, `adb devices` e comandos do sistema operacional local não são suportados aqui.")
        }
        val process = newShizukuProcess(command)
        val stdout = async(Dispatchers.IO) {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val stderr = async(Dispatchers.IO) {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        val ended = process.waitFor(35, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly()
            return@withContext "Tempo limite excedido (35 s). O processo foi encerrado."
        }
        val out = try { stdout.await() } catch (ignored: Throwable) { "" }.trim()
        val err = try { stderr.await() } catch (ignored: Throwable) { "" }.trim()
        buildString {
            append("exit code: ${process.exitValue()}\n")
            if (out.isNotEmpty()) append(out)
            if (err.isNotEmpty()) {
                if (out.isNotEmpty()) append("\n\n")
                append("stderr:\n").append(err)
            }
            if (out.isBlank() && err.isBlank()) append("(sem saída)")
        }.take(16_000)
    }
}
