package com.lippezin.dshagent.storage

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cofre Obsidian usado como memória de longo prazo (padrão "tabela de roteamento + notas atômicas"):
 * pastas numeradas, CLAUDE.md/AGENTS.md com roteamento por palavra-chave, frontmatter padrão e templates.
 * O cofre é um projeto do app; a raiz do cofre é detectada (pasta com .obsidian, CLAUDE.md ou AGENTS.md).
 */
class VaultMemory(private val storage: ProjectStorage) {

    data class NoteInfo(val path: String, val meta: Map<String, String>, val body: String)

    companion object {
        val FOLDERS = listOf("00-Inbox", "10-Trabalho", "20-Estudos", "30-Projetos", "50-Pessoal", "70-Daily", "99-Templates")
        val TYPES = listOf("nota", "meeting", "decisao", "estudo", "ideia", "daily")
        val STATUSES = listOf("inbox", "ativo", "arquivado")
        private const val MAX_NOTE_BYTES = 300_000L

        fun now(pattern: String): String = SimpleDateFormat(pattern, Locale.US).format(Date())

        /** kebab-case sem acento, como exigem as regras de escrita do cofre. */
        fun slug(title: String): String {
            val plain = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            return plain.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(80).ifBlank { "nota" }
        }

        fun parseFrontmatter(raw: String): Pair<Map<String, String>, String> {
            val text = raw.replace("\r\n", "\n")
            if (!text.startsWith("---\n")) return emptyMap<String, String>() to text
            val end = text.indexOf("\n---", 4)
            if (end < 0) return emptyMap<String, String>() to text
            val meta = LinkedHashMap<String, String>()
            text.substring(4, end).lines().forEach { line ->
                val i = line.indexOf(':')
                if (i > 0 && !line.startsWith(" ") && !line.startsWith("-")) {
                    meta[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('"')
                }
            }
            val body = text.substring(end + 4).trimStart('\n')
            return meta to body
        }

        private fun yamlText(value: String): String {
            val oneLine = value.replace('\n', ' ').trim()
            return if (oneLine.any { it == ':' || it == '#' || it == '"' } || oneLine.startsWith("[") || oneLine.startsWith("-"))
                "\"" + oneLine.replace("\"", "'") + "\"" else oneLine
        }

        fun links(body: String): List<String> =
            Regex("\\[\\[([^\\]|#]+)").findAll(body).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()

        /** Atualiza o campo `updated` sem mexer em id/created. */
        fun touchUpdated(raw: String): String {
            val text = raw.replace("\r\n", "\n")
            if (!text.startsWith("---\n")) return text
            val end = text.indexOf("\n---", 4)
            if (end < 0) return text
            val lines = text.substring(4, end).lines().toMutableList()
            val line = "updated: ${now("yyyy-MM-dd")}"
            val index = lines.indexOfFirst { it.startsWith("updated:") }
            if (index >= 0) lines[index] = line else lines.add(line)
            return "---\n" + lines.joinToString("\n") + text.substring(end)
        }
    }

    private fun join(root: String, rel: String) = if (root.isEmpty()) rel else "$root/$rel"

    /** Raiz do cofre dentro do projeto: pasta mais rasa com .obsidian, CLAUDE.md ou AGENTS.md; senão a raiz do projeto. */
    suspend fun root(projectId: String): String {
        val entries = storage.listEntries(projectId, limit = 3000, maxDepth = 4)
        val candidates = entries.mapNotNull { e ->
            val name = e.path.substringAfterLast('/')
            val parent = if (e.path.contains('/')) e.path.substringBeforeLast('/') else ""
            when {
                !e.isDir && (name.equals("CLAUDE.md", true) || name.equals("AGENTS.md", true)) -> parent
                e.isDir && name == ".obsidian" -> parent
                else -> null
            }
        }
        return candidates.minByOrNull { if (it.isEmpty()) 0 else it.count { ch -> ch == '/' } + 1 } ?: ""
    }

    /** Texto do CLAUDE.md (ou AGENTS.md) do cofre, com tabela de roteamento e regras de leitura/escrita. */
    suspend fun rules(projectId: String, vaultRoot: String): String {
        for (name in listOf("CLAUDE.md", "AGENTS.md")) {
            val path = join(vaultRoot, name)
            if (storage.exists(projectId, path)) {
                val text = runCatching { storage.readFile(projectId, path) }.getOrNull()
                if (!text.isNullOrBlank()) return text.take(9_000)
            }
        }
        return ""
    }

    private suspend fun notePaths(projectId: String, vaultRoot: String, folder: String?, includeTemplates: Boolean): List<String> {
        val prefix = (folder?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }?.let { join(vaultRoot, it) } ?: vaultRoot)
        return storage.listEntries(projectId, limit = 3000, maxDepth = 10)
            .filter { !it.isDir && it.path.endsWith(".md", ignoreCase = true) && it.size <= MAX_NOTE_BYTES }
            .map { it.path }
            .filter { path ->
                (prefix.isEmpty() || path.startsWith("$prefix/")) &&
                    !path.split('/').any { it == ".obsidian" || it == ".trash" } &&
                    (includeTemplates || folder != null || !path.contains("99-Templates/"))
            }
    }

    private suspend fun load(projectId: String, path: String): NoteInfo? {
        val text = runCatching { storage.readFile(projectId, path) }.getOrNull() ?: return null
        val (meta, body) = parseFrontmatter(text)
        return NoteInfo(path, meta, body)
    }

    private fun summary(note: NoteInfo, vaultRoot: String): String {
        val rel = if (vaultRoot.isEmpty()) note.path else note.path.removePrefix("$vaultRoot/")
        val m = note.meta
        val fields = listOf("area", "type", "status", "updated").mapNotNull { k -> m[k]?.takeIf { it.isNotBlank() }?.let { "$k=$it" } }
        val tags = m["tags"]?.takeIf { it.isNotBlank() }?.let { " tags=$it" }.orEmpty()
        val title = m["title"]?.takeIf { it.isNotBlank() } ?: note.path.substringAfterLast('/').removeSuffix(".md")
        return "$rel — $title [${fields.joinToString(", ")}]$tags"
    }

    suspend fun search(
        projectId: String, vaultRoot: String, query: String, folder: String?,
        area: String?, type: String?, status: String?, tag: String?, limit: Int
    ): String {
        val terms = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length >= 2 }
        val hasFilter = listOf(area, type, status, tag).any { !it.isNullOrBlank() }
        if (terms.isEmpty() && !hasFilter && folder.isNullOrBlank()) {
            return "Informe termos específicos (nome próprio, tecnologia, cliente, título) ou algum filtro (folder, area, type, status, tag)."
        }
        val max = limit.coerceIn(1, 10)
        data class Hit(val score: Int, val note: NoteInfo, val snippet: String?)
        val hits = ArrayList<Hit>()
        for (path in notePaths(projectId, vaultRoot, folder, includeTemplates = false).take(600)) {
            val note = load(projectId, path) ?: continue
            val m = note.meta
            if (!area.isNullOrBlank() && !m["area"].orEmpty().equals(area.trim(), true)) continue
            if (!type.isNullOrBlank() && !m["type"].orEmpty().equals(type.trim(), true)) continue
            if (!status.isNullOrBlank() && !m["status"].orEmpty().equals(status.trim(), true)) continue
            if (!tag.isNullOrBlank() && !m["tags"].orEmpty().lowercase(Locale.ROOT).contains(tag.trim().lowercase(Locale.ROOT))) continue
            var score = 0
            var snippet: String? = null
            if (terms.isEmpty()) {
                score = 1
            } else {
                val title = (m["title"].orEmpty() + " " + path.substringAfterLast('/')).lowercase(Locale.ROOT)
                val tags = m["tags"].orEmpty().lowercase(Locale.ROOT)
                val lowerBody = note.body.lowercase(Locale.ROOT)
                for (t in terms) {
                    if (title.contains(t)) score += 5
                    if (tags.contains(t)) score += 3
                    if (path.lowercase(Locale.ROOT).contains(t)) score += 2
                    val idx = lowerBody.indexOf(t)
                    if (idx >= 0) {
                        score += 1
                        if (snippet == null) {
                            val lineStart = lowerBody.lastIndexOf('\n', idx) + 1
                            val lineEnd = lowerBody.indexOf('\n', idx).let { if (it < 0) lowerBody.length else it }
                            snippet = note.body.substring(lineStart, lineEnd).trim().take(160)
                        }
                    }
                }
            }
            if (score > 0) hits += Hit(score, note, snippet)
        }
        if (hits.isEmpty()) return "Nenhuma nota encontrada. Tente outros termos específicos ou liste uma pasta com vault_list."
        val sorted = if (terms.isEmpty()) hits.sortedByDescending { it.note.meta["updated"].orEmpty() } else hits.sortedByDescending { it.score }
        return buildString {
            append("${minOf(max, sorted.size)} de ${sorted.size} resultado(s) (leia só os mais relevantes com vault_read):\n")
            sorted.take(max).forEach { h ->
                append("- ").append(summary(h.note, vaultRoot)).append('\n')
                if (h.snippet != null) append("    «").append(h.snippet).append("»\n")
            }
        }
    }

    suspend fun list(projectId: String, vaultRoot: String, folder: String?): String {
        val paths = notePaths(projectId, vaultRoot, folder, includeTemplates = true)
        if (paths.isEmpty()) return "Nenhuma nota${if (folder.isNullOrBlank()) "" else " em $folder"}."
        return buildString {
            append("${paths.size} nota(s)${if (paths.size > 60) " (mostrando 60)" else ""}:\n")
            paths.take(60).forEach { p ->
                val note = load(projectId, p)
                append("- ").append(if (note != null) summary(note, vaultRoot) else p).append('\n')
            }
        }
    }

    suspend fun read(projectId: String, vaultRoot: String, path: String, frontmatterOnly: Boolean): String {
        val full = join(vaultRoot, path.trim().trim('/'))
        val note = load(projectId, full) ?: throw IllegalArgumentException("Nota não encontrada: $path")
        val metaText = if (note.meta.isEmpty()) "(sem frontmatter)" else note.meta.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        if (frontmatterOnly) return "Frontmatter de $path:\n$metaText"
        val links = links(note.body)
        return buildString {
            append("Frontmatter:\n").append(metaText).append("\n\nConteúdo:\n").append(note.body)
            if (links.isNotEmpty()) append("\n\nLinks desta nota: ").append(links.joinToString(", ") { "[[$it]]" })
        }
    }

    suspend fun write(
        projectId: String, vaultRoot: String, title: String, content: String, folder: String?,
        type: String?, area: String?, tags: List<String>, status: String?
    ): String {
        require(title.isNotBlank()) { "Informe o título da nota." }
        val cleanType = type?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in TYPES } ?: "nota"
        val explicitFolder = folder?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }
        val targetFolder = explicitFolder ?: if (cleanType == "daily") "70-Daily" else "00-Inbox"
        val inbox = targetFolder.startsWith("00-Inbox")
        val cleanStatus = if (inbox && explicitFolder == null) "inbox"
        else status?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in STATUSES } ?: if (inbox) "inbox" else "ativo"
        val fileName = (if (cleanType == "daily" && Regex("\\d{4}-\\d{2}-\\d{2}").matches(title.trim())) title.trim() else slug(title)) + ".md"
        val rel = "$targetFolder/$fileName"
        val full = join(vaultRoot, rel)
        require(!storage.exists(projectId, full)) { "Já existe uma nota em $rel. Use vault_update para acrescentar ou alterar." }
        val tagList = tags.map { slug(it) }.filter { it.isNotEmpty() }.distinct()
        val today = now("yyyy-MM-dd")
        val text = buildString {
            append("---\n")
            append("id: ").append(now("yyyyMMdd-HHmm")).append('\n')
            append("title: ").append(yamlText(title)).append('\n')
            append("area: ").append(yamlText(area.orEmpty())).append('\n')
            append("type: ").append(cleanType).append('\n')
            append("tags: [").append(tagList.joinToString(", ")).append("]\n")
            append("status: ").append(cleanStatus).append('\n')
            append("created: ").append(today).append('\n')
            append("updated: ").append(today).append('\n')
            append("---\n\n")
            append(content.trim()).append('\n')
        }
        storage.writeFile(projectId, full, text)
        return "Nota criada: $rel (status=$cleanStatus). Conecte-a a notas relacionadas com [[wikilinks]]."
    }

    /** mode = "append" (acrescenta ao fim) ou "replace" (substitui o corpo, preservando o frontmatter). Atualiza `updated`. */
    suspend fun update(projectId: String, vaultRoot: String, path: String, content: String, mode: String): String {
        val rel = path.trim().trim('/')
        val full = join(vaultRoot, rel)
        val raw = runCatching { storage.readFile(projectId, full) }.getOrNull()
            ?: throw IllegalArgumentException("Nota não encontrada: $rel")
        val normalized = raw.replace("\r\n", "\n")
        val newText = if (mode == "replace") {
            val bodyStart = if (normalized.startsWith("---\n")) {
                val end = normalized.indexOf("\n---", 4)
                if (end >= 0) end + 4 else 0
            } else 0
            normalized.substring(0, bodyStart).trimEnd('\n') + (if (bodyStart > 0) "\n\n" else "") + content.trim() + "\n"
        } else {
            normalized.trimEnd('\n') + "\n\n" + content.trim() + "\n"
        }
        storage.writeFile(projectId, full, touchUpdated(newText))
        return "Nota atualizada ($mode): $rel"
    }

    // ---------- Criação do cofre ----------

    private fun routingTable(): String = """
| Palavra-chave | Caminho |
|---|---|
| inbox, rascunho, solto, "anota aí", captura rápida | 00-Inbox/ |
| trabalho, empresa, cliente, reunião, meeting, entrega, sprint, deadline | 10-Trabalho/ |
| estudo, curso, aula, livro, leitura, resumo | 20-Estudos/ |
| projeto, side project, produto, repo, roadmap, feature | 30-Projetos/ |
| pessoal, saúde, finanças, casa, família, viagem, hobby | 50-Pessoal/ |
| daily, hoje, diário, log do dia, retrospectiva do dia | 70-Daily/ |
| template, modelo, estrutura de nota, scaffold | 99-Templates/ |
""".trim()

    private fun rulesFile(agent: String): String = """
# Memória — vault Obsidian como memória de agentes

Este vault é a memória de longo prazo do usuário. Agentes leem e escrevem aqui seguindo as regras abaixo ($agent). Índice de entrada: [[HOME]].

## Tabela de roteamento (palavra-chave → caminho direto)

Quando a palavra-chave aparecer no pedido, vá direto ao caminho. Não faça busca no vault inteiro antes.

${routingTable()}

Sem palavra-chave clara: 00-Inbox/ com `status: inbox`.

## Regras de leitura

- Nunca ler o vault inteiro; nada de abrir todos os arquivos.
- Buscar antes, com termos específicos (nome próprio, tecnologia, cliente, título), nunca termos genéricos como "nota" ou "projeto".
- Se a palavra-chave bater na tabela, listar só aquela pasta em vez de buscar.
- Ler o frontmatter antes do corpo e filtrar por ele (area, type, status, tags, updated).
- Ler no máximo as 3 a 5 notas mais relevantes.

## Regras de escrita

- Notas atômicas: 1 conceito = 1 arquivo.
- Nome de arquivo em kebab-case, sem acento.
- Nota sem destino explícito nasce em 00-Inbox/ com `status: inbox`.
- Conectar notas relacionadas com wikilinks `[[assim]]`.
- Ao modificar uma nota, atualizar o campo `updated` (sem tocar em `id` e `created`).
- Escrever sempre em português com acentuação correta.

## Frontmatter padrão

```yaml
id: YYYYMMDD-HHMM
title:
area:
type: nota | meeting | decisao | estudo | ideia | daily
tags: []
status: inbox | ativo | arquivado
created: YYYY-MM-DD
updated: YYYY-MM-DD
```
""".trim() + "\n"

    private fun template(type: String, sections: List<String>): String = buildString {
        append("---\nid:\ntitle:\narea:\ntype: ").append(type).append("\ntags: []\nstatus: inbox\ncreated:\nupdated:\n---\n\n")
        sections.forEach { append("## ").append(it).append("\n\n") }
    }

    /** Cria pastas, CLAUDE.md, AGENTS.md, HOME.md e templates que ainda não existem. Não sobrescreve nada. */
    suspend fun scaffold(projectId: String, vaultRoot: String = ""): String {
        val created = ArrayList<String>()
        suspend fun ensure(rel: String, content: String) {
            val full = join(vaultRoot, rel)
            if (!storage.exists(projectId, full)) {
                storage.writeFile(projectId, full, content)
                created += rel
            }
        }
        FOLDERS.forEach { ensure("$it/.keep", "") }
        ensure("CLAUDE.md", rulesFile("Claude Code"))
        ensure("AGENTS.md", rulesFile("Codex, OpenCode e qualquer agente que leia AGENTS.md"))
        ensure("99-Templates/_nota.md", template("nota", listOf("Contexto", "Conteúdo", "Relacionadas")))
        ensure("99-Templates/_daily.md", template("daily", listOf("Foco", "Feito", "Aberto", "Notas do dia")))
        ensure("99-Templates/_decisao.md", template("decisao", listOf("Contexto", "Opções", "Decisão", "Consequências", "Relacionadas")))
        ensure(
            "HOME.md",
            "# HOME\n\nÍndice de entrada do vault. Regras e roteamento em [[CLAUDE]] e [[AGENTS]].\n\n" +
                FOLDERS.filter { it != "99-Templates" }.joinToString("\n") { "- $it/" } + "\n"
        )
        return if (created.isEmpty()) "O cofre já estava completo; nada foi alterado."
        else "Cofre configurado: ${created.size} item(ns) criado(s)."
    }
}
