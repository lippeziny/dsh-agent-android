package com.lippezin.dshagent.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lippezin.dshagent.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ZipReport(
    val folder: String,
    val extracted: Boolean,
    val files: Int,
    val skipped: Int,
    val bytes: Long,
    val truncated: Boolean,
    val tree: List<String>,
    val previews: List<Pair<String, String>>
)

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

    // ---------- Backends ----------

    private fun safeSegments(path: String): List<String> {
        val segments = path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
        require(segments.isNotEmpty()) { "Informe um caminho de arquivo." }
        require(segments.none { it == ".." }) { "Caminho inválido." }
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

    private fun externalBackend(projectId: String): ProjectBackend? {
        val tree = prefs.getWorkspaceTreeUri() ?: return null
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree)) }.getOrNull() ?: return null
        if (!root.canRead()) return null
        val container = root.findFile("DSH Projects") ?: root.createDirectory("DSH Projects") ?: return null
        val project = container.findFile(projectId) ?: container.createDirectory(projectId) ?: return null
        return DocBackend(context, project)
    }

    private fun backend(projectId: String): ProjectBackend =
        externalBackend(projectId) ?: InternalBackend(internalProject(projectId))

    // ---------- Listagem e leitura ----------

    suspend fun listEntries(projectId: String, limit: Int = 3000, maxDepth: Int = 10): List<FileEntry> =
        withContext(Dispatchers.IO) {
            backend(projectId).list(limit, maxDepth).sortedBy { it.path.lowercase() }
        }

    suspend fun listFiles(projectId: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            backend(projectId).list(3000, 10).filter { !it.isDir }.map { it.path }.sorted()
        }.getOrElse { listOf("[erro ao listar arquivos: ${it.message ?: "desconhecido"}]") }
    }

    private fun readText(be: ProjectBackend, segments: List<String>, displayPath: String): String {
        val st = be.stat(segments) ?: throw IllegalArgumentException("Arquivo não encontrado: $displayPath")
        require(!st.isDir) { "O caminho é uma pasta, não um arquivo." }
        require(st.size <= 2_000_000L) { "Arquivo grande demais (${st.size} bytes; limite de leitura 2 MB)." }
        val bytes = be.openRead(segments)?.use { it.readBytes() }
            ?: throw IllegalStateException("Não foi possível ler o arquivo.")
        require(!ZipUtils.looksBinary(bytes)) { "O arquivo parece binário e não pode ser lido como texto." }
        return String(bytes, Charsets.UTF_8)
    }

    suspend fun readFile(projectId: String, relativePath: String, startLine: Int? = null, endLine: Int? = null): String =
        withContext(Dispatchers.IO) {
            val segments = safeSegments(relativePath)
            val text = readText(backend(projectId), segments, relativePath)
            if (startLine == null && endLine == null) return@withContext text
            val lines = text.lines()
            val from = (startLine ?: 1).coerceAtLeast(1)
            val to = (endLine ?: lines.size).coerceAtMost(lines.size)
            require(from <= to) { "Intervalo de linhas inválido (arquivo tem ${lines.size} linhas)." }
            "Linhas $from-$to de ${lines.size}:\n" + lines.subList(from - 1, to).joinToString("\n")
        }

    // ---------- Escrita e manipulação ----------

    suspend fun writeFile(projectId: String, relativePath: String, content: String): String =
        withContext(Dispatchers.IO) {
            val segments = safeSegments(relativePath)
            require(content.length <= 1_500_000) { "Arquivo grande demais (limite de 1,5 milhão de caracteres)." }
            val bytes = content.toByteArray(Charsets.UTF_8)
            backend(projectId).write(segments) { it.write(bytes) }
            "Arquivo salvo: ${segments.joinToString("/")} (${bytes.size} bytes)"
        }

    suspend fun editFile(projectId: String, relativePath: String, oldText: String, newText: String, replaceAll: Boolean): String =
        withContext(Dispatchers.IO) {
            require(oldText.isNotEmpty()) { "old_text não pode ser vazio." }
            val segments = safeSegments(relativePath)
            val be = backend(projectId)
            val content = readText(be, segments, relativePath)
            var count = 0
            var index = 0
            while (true) {
                index = content.indexOf(oldText, index)
                if (index < 0) break
                count++
                index += oldText.length
            }
            require(count > 0) { "O trecho old_text não foi encontrado em $relativePath. Leia o arquivo de novo e copie o trecho exatamente." }
            require(count == 1 || replaceAll) { "O trecho aparece $count vezes; torne-o mais específico ou use replace_all=true." }
            val updated = content.replace(oldText, newText)
            require(updated.length <= 1_500_000) { "Resultado grande demais." }
            val bytes = updated.toByteArray(Charsets.UTF_8)
            be.write(segments) { it.write(bytes) }
            "Editado: ${segments.joinToString("/")} ($count substituição(ões))"
        }

    suspend fun deletePath(projectId: String, relativePath: String): String = withContext(Dispatchers.IO) {
        val segments = safeSegments(relativePath)
        val be = backend(projectId)
        val st = be.stat(segments) ?: throw IllegalArgumentException("Caminho não encontrado: $relativePath")
        check(be.delete(segments)) { "Não foi possível excluir $relativePath." }
        if (st.isDir) "Pasta excluída: $relativePath" else "Arquivo excluído: $relativePath"
    }

    suspend fun movePath(projectId: String, from: String, to: String): String = withContext(Dispatchers.IO) {
        val src = safeSegments(from)
        val dst = safeSegments(to)
        val be = backend(projectId)
        val st = be.stat(src) ?: throw IllegalArgumentException("Origem não encontrada: $from")
        require(!st.isDir) { "Mover pastas não é suportado; mova arquivo por arquivo." }
        require(be.stat(dst) == null) { "O destino já existe: $to" }
        val input = be.openRead(src) ?: throw IllegalStateException("Não foi possível ler a origem.")
        input.use { stream -> be.write(dst) { out -> stream.copyTo(out) } }
        be.delete(src)
        "Movido: ${src.joinToString("/")} → ${dst.joinToString("/")}"
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        backend(projectId).deleteAll()
    }

    suspend fun searchText(projectId: String, query: String, maxResults: Int = 40): String =
        withContext(Dispatchers.IO) {
            require(query.isNotBlank()) { "Informe o texto a procurar." }
            val be = backend(projectId)
            val q = query.lowercase()
            val hits = ArrayList<String>()
            var scanned = 0
            for (file in be.list(3000, 10)) {
                if (file.isDir || file.size > 1_000_000L) continue
                if (!ZipUtils.isTextName(file.path.substringAfterLast('/'))) continue
                if (scanned++ >= 800) break
                val text = runCatching {
                    be.openRead(file.path.split('/'))?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull() ?: continue
                var lineNo = 0
                for (line in text.lineSequence()) {
                    lineNo++
                    if (line.lowercase().contains(q)) {
                        hits += "${file.path}:$lineNo: ${line.trim().take(160)}"
                        if (hits.size >= maxResults) break
                    }
                }
                if (hits.size >= maxResults) break
            }
            if (hits.isEmpty()) "Nenhum resultado para \"$query\"." else hits.joinToString("\n")
        }

    /** Lê um arquivo de texto de até [maxBytes] (ex.: graph.json do graphify). Devolve null se não existir ou for grande demais. */
    suspend fun readTextLimited(projectId: String, relativePath: String, maxBytes: Long): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val segments = safeSegments(relativePath)
                val be = backend(projectId)
                val st = be.stat(segments) ?: return@runCatching null
                if (st.isDir || st.size > maxBytes) return@runCatching null
                val bytes = be.openRead(segments)?.use { it.readBytes() } ?: return@runCatching null
                if (ZipUtils.looksBinary(bytes)) null else String(bytes, Charsets.UTF_8)
            }.getOrNull()
        }

    suspend fun exists(projectId: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { backend(projectId).stat(safeSegments(relativePath)) != null }.getOrDefault(false)
    }

    // ---------- Memória ----------

    suspend fun readMemory(projectId: String): String = withContext(Dispatchers.IO) {
        try { readFile(projectId, "MEMORY.md") } catch (ignored: Throwable) { "" }
    }

    suspend fun saveMemory(projectId: String, content: String): String {
        val old = readMemory(projectId)
        val combined = when {
            old.isBlank() -> "# Memória do projeto\n\n$content"
            else -> old.trimEnd() + "\n\n---\n\n" + content.trim()
        }
        return writeFile(projectId, "MEMORY.md", combined)
    }

    // ---------- ZIP ----------

    private fun copyToTemp(uri: Uri): File {
        val tmp = File.createTempFile("zip_import_", ".zip", context.cacheDir)
        try {
            var copied = 0L
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Não foi possível abrir o ZIP.")
            input.use { stream ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        copied += n
                        require(copied <= ZipUtils.MAX_ZIP_FILE_BYTES) { "O ZIP é maior que 80 MB." }
                        out.write(buffer, 0, n)
                    }
                }
            }
            return tmp
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Lê um ZIP. Com [extract] = true, extrai para uma subpasta do projeto (remove a pasta raiz única,
     * ignora lixo como .git/node_modules e caminhos inseguros). Com false, apenas inspeciona e devolve trechos.
     */
    suspend fun processZip(
        projectId: String,
        uri: Uri,
        requestedName: String,
        extract: Boolean,
        previewBudget: Int = 60_000
    ): ZipReport = withContext(Dispatchers.IO) {
        val tmp = copyToTemp(uri)
        try {
            ZipFile(tmp).use { zf ->
                val entries = Collections.list(zf.entries()).filter { !it.isDirectory }
                val cleaned = entries.mapNotNull { ZipUtils.cleanSegments(it.name) }
                if (cleaned.isEmpty()) throw IllegalArgumentException("O ZIP está vazio ou não tem arquivos válidos.")
                val strip = cleaned.map { it.first() }.toSet().size == 1 && cleaned.all { it.size > 1 }

                val be: ProjectBackend? = if (extract) backend(projectId) else null
                var folder = ZipUtils.safeFolderName(requestedName)
                if (be != null) {
                    var candidate = folder
                    var n = 2
                    while (be.stat(listOf(candidate)) != null && n < 100) {
                        candidate = "$folder-$n"
                        n++
                    }
                    folder = candidate
                }

                val tree = ArrayList<String>()
                val previews = ArrayList<Pair<String, String>>()
                var previewLeft = previewBudget
                var files = 0
                var skipped = 0
                var total = 0L
                var truncated = false

                for (entry in entries) {
                    val base = ZipUtils.cleanSegments(entry.name)
                    val segs = if (base != null && strip) base.drop(1) else base
                    if (segs == null || segs.isEmpty() || ZipUtils.shouldSkip(segs)) {
                        skipped++
                        continue
                    }
                    if (files >= ZipUtils.MAX_ENTRIES || total >= ZipUtils.MAX_TOTAL_BYTES) {
                        truncated = true
                        skipped++
                        continue
                    }
                    val data = zf.getInputStream(entry).use { readLimited(it, ZipUtils.MAX_ENTRY_BYTES) }
                    if (data == null) {
                        skipped++
                        continue
                    }
                    total += data.size
                    files++
                    val rel = segs.joinToString("/")
                    val full = if (extract) "$folder/$rel" else rel
                    if (tree.size < 800) tree += full
                    if (be != null) be.write(listOf(folder) + segs) { it.write(data) }

                    val fileName = segs.last()
                    if (previewLeft > 0 && data.size <= 200_000 && ZipUtils.isTextName(fileName) && !ZipUtils.looksBinary(data)) {
                        val wanted = if (extract) ZipUtils.isKeyFile(fileName) && previews.size < 8 else true
                        if (wanted) {
                            val cap = if (extract) 6_000 else minOf(previewLeft, 12_000)
                            val text = String(data, Charsets.UTF_8).take(cap)
                            previews += full to text
                            previewLeft -= text.length
                        }
                    }
                }
                ZipReport(folder, extract, files, skipped, total, truncated, tree, previews)
            }
        } finally {
            tmp.delete()
        }
    }

    suspend fun exportZip(projectId: String, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val be = backend(projectId)
        val files = be.list(20_000, 12).filter { !it.isDir }
        var written = 0
        ZipOutputStream(BufferedOutputStream(out)).use { zos ->
            for (file in files) {
                val input = be.openRead(file.path.split('/')) ?: continue
                input.use { stream ->
                    zos.putNextEntry(ZipEntry(file.path))
                    stream.copyTo(zos)
                    zos.closeEntry()
                }
                written++
            }
        }
        written
    }
}
