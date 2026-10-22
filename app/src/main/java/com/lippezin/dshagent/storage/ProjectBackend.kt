package com.lippezin.dshagent.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class FileEntry(val path: String, val isDir: Boolean, val size: Long, val modified: Long)

/** Mime genérico: evita que provedores SAF acrescentem ".txt" ao nome do arquivo. */
internal const val GENERIC_MIME = "application/octet-stream"

internal interface ProjectBackend {
    fun list(limit: Int, maxDepth: Int): List<FileEntry>
    fun stat(segments: List<String>): FileEntry?
    fun openRead(segments: List<String>): InputStream?
    fun write(segments: List<String>, block: (OutputStream) -> Unit)
    fun delete(segments: List<String>): Boolean
    fun deleteAll()
}

internal class InternalBackend(private val base: File) : ProjectBackend {
    private val basePath: String = base.canonicalPath

    private fun resolve(segments: List<String>): File {
        val file = segments.fold(base) { current, next -> File(current, next) }.canonicalFile
        require(file.path == basePath || file.path.startsWith(basePath + File.separator)) { "Caminho fora do projeto." }
        return file
    }

    override fun list(limit: Int, maxDepth: Int): List<FileEntry> =
        base.walkTopDown()
            .maxDepth(maxDepth)
            .filter { it != base }
            .take(limit)
            .map {
                FileEntry(
                    path = it.relativeTo(base).invariantSeparatorsPath,
                    isDir = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    modified = it.lastModified()
                )
            }
            .toList()

    override fun stat(segments: List<String>): FileEntry? {
        val file = resolve(segments)
        if (!file.exists()) return null
        return FileEntry(segments.joinToString("/"), file.isDirectory, if (file.isFile) file.length() else 0L, file.lastModified())
    }

    override fun openRead(segments: List<String>): InputStream? {
        val file = resolve(segments)
        return if (file.isFile) file.inputStream() else null
    }

    override fun write(segments: List<String>, block: (OutputStream) -> Unit) {
        val file = resolve(segments)
        file.parentFile?.mkdirs()
        file.outputStream().use(block)
    }

    override fun delete(segments: List<String>): Boolean {
        val file = resolve(segments)
        if (file.path == basePath) return false
        return file.deleteRecursively()
    }

    override fun deleteAll() {
        base.deleteRecursively()
    }
}

internal class DocBackend(private val context: Context, private val root: DocumentFile) : ProjectBackend {
    private fun find(segments: List<String>): DocumentFile? {
        var node: DocumentFile = root
        for (segment in segments) {
            node = node.findFile(segment) ?: return null
        }
        return node
    }

    override fun list(limit: Int, maxDepth: Int): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        fun walk(dir: DocumentFile, prefix: String, depth: Int) {
            if (depth > maxDepth || out.size >= limit) return
            dir.listFiles().sortedBy { it.name ?: "" }.forEach { file ->
                if (out.size >= limit) return
                val name = file.name ?: return@forEach
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                out += FileEntry(path, file.isDirectory, if (file.isFile) file.length() else 0L, file.lastModified())
                if (file.isDirectory) walk(file, path, depth + 1)
            }
        }
        walk(root, "", 1)
        return out
    }

    override fun stat(segments: List<String>): FileEntry? {
        val node = find(segments) ?: return null
        return FileEntry(segments.joinToString("/"), node.isDirectory, if (node.isFile) node.length() else 0L, node.lastModified())
    }

    override fun openRead(segments: List<String>): InputStream? {
        val node = find(segments) ?: return null
        if (!node.isFile) return null
        return context.contentResolver.openInputStream(node.uri)
    }

    override fun write(segments: List<String>, block: (OutputStream) -> Unit) {
        var parent: DocumentFile = root
        segments.dropLast(1).forEach { segment ->
            parent = parent.findFile(segment) ?: parent.createDirectory(segment)
                ?: throw IllegalStateException("Não foi possível criar a pasta $segment")
        }
        val name = segments.last()
        val target = parent.findFile(name) ?: parent.createFile(GENERIC_MIME, name)
            ?: throw IllegalStateException("Não foi possível criar o arquivo $name")
        val stream = context.contentResolver.openOutputStream(target.uri, "wt")
            ?: throw IllegalStateException("Não foi possível gravar o arquivo $name")
        stream.use(block)
    }

    override fun delete(segments: List<String>): Boolean = find(segments)?.delete() ?: false

    override fun deleteAll() {
        root.delete()
    }
}
