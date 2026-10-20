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
    } catch (_: Throwable) {
        "Shizuku indisponível"
    }

    fun requestPermission() {
        check(Shizuku.pingBinder()) { "Abra o Shizuku e inicie o serviço primeiro." }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(7421)
        }
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
            throw IllegalArgumentException("Este app executa comandos no shell do próprio Android. Para comandos locais, use `adb shell <comando>`; `adb install`, `adb devices` e comandos do computador não rodam no aparelho.")
        }
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
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
        val out = try { stdout.await() } catch (_: Throwable) { "" }.trim()
        val err = try { stderr.await() } catch (_: Throwable) { "" }.trim()
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
