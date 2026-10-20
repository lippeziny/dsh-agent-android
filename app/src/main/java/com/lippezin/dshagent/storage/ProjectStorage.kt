package com.lippezin.dshagent.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lippezin.dshagent.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

class ProjectStorage(private val context: Context, private val prefs: AppPrefs) {
    private val internalRoot: File get() = File(context.filesDir, "projects")

    fun persistWorkspacePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.setWorkspaceTreeUri(uri.toString())
    }

    private fun safeSegments(path: String): List<String> {
        val segments = path.replace('\\', '/').split('/').filter { it.isNotBlank() }
        require(segments.isNotEmpty()) { "Informe um caminho de arquivo." }
        require(segments.none { it == "." || it == ".." }) { "Caminho inválido." }
        require(segments.all { !it.contains(':') && it.length <= 120 }) { "Nome de arquivo inválido." }
        return segments
    }

    private fun internalProject(projectId: String): File {
        val root = File(internalRoot, projectId).canonicalFile
        val safeBase = internalRoot.canonicalFile
        require(root.path.startsWith(safeBase.path + File.separator)) { "Pasta de projeto inválida." }
        root.mkdirs()
        return root
    }

    private fun externalProject(projectId: String, create: Boolean): DocumentFile? {
        val tree = prefs.getWorkspaceTreeUri() ?: return null
        var root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree)) }.getOrNull() ?: return null
        if (!root.canRead()) return null
        root = child(root, "DSH Projects", create) ?: return null
        return child(root, projectId, create)
    }

    private fun child(parent: DocumentFile, name: String, create: Boolean): DocumentFile? {
        val existing = parent.findFile(name)
        if (existing != null) return existing
        return if (create) parent.createDirectory(name) else null
    }

    suspend fun listFiles(projectId: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val external = externalProject(projectId, true)
            if (external != null) {
                val out = mutableListOf<String>()
                fun walk(dir: DocumentFile, prefix: String, depth: Int) {
                    if (depth > 4 || out.size > 300) return
                    dir.listFiles().sortedBy { it.name ?: "" }.forEach { file ->
                        val name = file.name ?: return@forEach
                        val path = if (prefix.isEmpty()) name else "$prefix/$name"
                        if (file.isDirectory) walk(file, path, depth + 1) else out += path
                    }
                }
                walk(external, "", 0)
                out
            } else {
                val base = internalProject(projectId)
                val out = mutableListOf<String>()
                base.walkTopDown().maxDepth(5).filter { it.isFile }.take(300).forEach {
                    out += it.relativeTo(base).invariantSeparatorsPath
                }
                out.sorted()
            }
        }.getOrElse { listOf("[erro ao listar arquivos: ${it.message ?: "desconhecido"}]") }
    }

    suspend fun readFile(projectId: String, relativePath: String): String = withContext(Dispatchers.IO) {
        val segments = safeSegments(relativePath)
        val external = externalProject(projectId, false)
        if (external != null) {
            var node: DocumentFile = external
            segments.forEachIndexed { index, segment ->
                node = node.findFile(segment) ?: throw IllegalArgumentException("Arquivo não encontrado: $relativePath")
                if (index < segments.lastIndex && !node.isDirectory) throw IllegalArgumentException("Caminho não é uma pasta.")
            }
            require(node.isFile) { "O caminho não é um arquivo." }
            context.contentResolver.openInputStream(node.uri)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("Não foi possível ler o arquivo.")
        } else {
            val base = internalProject(projectId)
            val file = segments.fold(base) { current, next -> File(current, next) }.canonicalFile
            require(file.path.startsWith(base.canonicalPath + File.separator)) { "Caminho fora do projeto." }
            require(file.isFile) { "Arquivo não encontrado: $relativePath" }
            file.readText(Charsets.UTF_8)
        }
    }

    suspend fun writeFile(projectId: String, relativePath: String, content: String): String = withContext(Dispatchers.IO) {
        val segments = safeSegments(relativePath)
        require(content.length <= 1_500_000) { "Arquivo grande demais (limite de 1,5 milhão de caracteres)." }
        val external = externalProject(projectId, true)
        if (external != null) {
            var parent: DocumentFile = external
            segments.dropLast(1).forEach { segment -> parent = child(parent, segment, true) ?: error("Não foi possível criar a pasta $segment") }
            val filename = segments.last()
            val existing = parent.findFile(filename)
            val target = existing ?: parent.createFile(mimeFor(filename), filename)
                ?: throw IllegalStateException("Não foi possível criar o arquivo.")
            context.contentResolver.openOutputStream(target.uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                ?: throw IllegalStateException("Não foi possível gravar o arquivo.")
        } else {
            val base = internalProject(projectId)
            val file = segments.fold(base) { current, next -> File(current, next) }.canonicalFile
            require(file.path.startsWith(base.canonicalPath + File.separator)) { "Caminho fora do projeto." }
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }
        "Arquivo salvo: $relativePath"
    }

    suspend fun readMemory(projectId: String): String = withContext(Dispatchers.IO) {
        try { readFile(projectId, "MEMORY.md") } catch (_: Throwable) { "" }
    }

    suspend fun saveMemory(projectId: String, content: String): String {
        val old = readMemory(projectId)
        val combined = when {
            old.isBlank() -> "# Memória do projeto\n\n$content"
            else -> old.trimEnd() + "\n\n---\n\n" + content.trim()
        }
        return writeFile(projectId, "MEMORY.md", combined)
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "md", "txt", "kt", "java", "json", "xml", "py", "js", "ts", "html", "css", "csv", "yml", "yaml", "sh" -> "text/plain"
        else -> "application/octet-stream"
    }
}
