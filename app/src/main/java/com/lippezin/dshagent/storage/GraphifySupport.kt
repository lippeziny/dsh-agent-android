package com.lippezin.dshagent.storage

import org.json.JSONObject
import java.util.Locale

/**
 * Compatibilidade com o graphify (grafo de conhecimento do código gerado no PC: `graphify extract .`).
 * O app não gera o grafo; lê `graphify-out/GRAPH_REPORT.md` e `graphify-out/graph.json` do projeto
 * (ex.: importados por ZIP) para o agente navegar pelo grafo em vez de varrer arquivos.
 */
class GraphifySupport(private val storage: ProjectStorage, private val projectId: String, entries: List<FileEntry>) {

    /** Pasta graphify-out dentro do projeto (ex.: "graphify-out" ou "meu-repo/graphify-out"), ou null. */
    val outDir: String? = entries
        .filter { !it.isDir && it.path.endsWith("graphify-out/GRAPH_REPORT.md", ignoreCase = true) }
        .minByOrNull { it.path.length }
        ?.path?.removeSuffix("/GRAPH_REPORT.md")?.removeSuffix("GRAPH_REPORT.md")

    private val hasGraphJson: Boolean = outDir != null && entries.any { it.path.equals("$outDir/graph.json", ignoreCase = true) }

    val available: Boolean get() = outDir != null
    val queryable: Boolean get() = hasGraphJson

    private class Node(val id: String, val label: String, val file: String, val community: String)
    private class Edge(val other: String, val relation: String, val outgoing: Boolean)

    private var nodes: Map<String, Node>? = null
    private var adjacency: Map<String, List<Edge>> = emptyMap()
    private var loadError: String? = null

    suspend fun reportExcerpt(maxChars: Int = 5000): String {
        val dir = outDir ?: return ""
        val report = storage.readTextLimited(projectId, "$dir/GRAPH_REPORT.md", 2_000_000L) ?: return ""
        return report.take(maxChars)
    }

    private suspend fun ensureLoaded() {
        if (nodes != null || loadError != null) return
        val dir = outDir
        if (dir == null) { loadError = "Este projeto não tem graphify-out."; return }
        val raw = storage.readTextLimited(projectId, "$dir/graph.json", 12_000_000L)
        if (raw == null) { loadError = "graph.json ausente ou maior que 12 MB."; return }
        try {
            val root = JSONObject(raw)
            val nodeArray = root.optJSONArray("nodes")
            val edgeArray = root.optJSONArray("links") ?: root.optJSONArray("edges")
            if (nodeArray == null) { loadError = "graph.json sem lista de nós (formato inesperado)."; return }
            val map = LinkedHashMap<String, Node>()
            for (i in 0 until nodeArray.length()) {
                val n = nodeArray.optJSONObject(i) ?: continue
                val id = n.opt("id")?.toString() ?: continue
                val label = n.optString("label").ifBlank { n.optString("name") }.ifBlank { id }
                val file = n.optString("source_file").ifBlank { n.optString("file") }.ifBlank { n.optString("path") }
                val community = n.opt("community")?.toString().orEmpty()
                map[id] = Node(id, label, file, community)
            }
            val adj = HashMap<String, MutableList<Edge>>()
            if (edgeArray != null) {
                for (i in 0 until edgeArray.length()) {
                    val e = edgeArray.optJSONObject(i) ?: continue
                    val s = e.opt("source")?.toString() ?: continue
                    val t = e.opt("target")?.toString() ?: continue
                    val rel = e.optString("relation").ifBlank { e.optString("label") }.ifBlank { e.optString("type") }.ifBlank { "relacionado a" }
                    adj.getOrPut(s) { ArrayList() }.add(Edge(t, rel, true))
                    adj.getOrPut(t) { ArrayList() }.add(Edge(s, rel, false))
                }
            }
            nodes = map
            adjacency = adj
        } catch (e: Throwable) {
            loadError = "Não foi possível ler graph.json: ${e.message ?: "erro desconhecido"}"
        }
    }

    suspend fun query(term: String, limit: Int): String {
        ensureLoaded()
        val all = nodes ?: return loadError ?: "Grafo indisponível."
        val terms = term.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(terms.isNotEmpty()) { "Informe o termo (símbolo, arquivo ou conceito) a procurar no grafo." }
        val matches = all.values.filter { n ->
            val hay = (n.label + " " + n.id + " " + n.file).lowercase(Locale.ROOT)
            terms.all { hay.contains(it) }
        }.sortedByDescending { adjacency[it.id]?.size ?: 0 }
        if (matches.isEmpty()) return "Nenhum nó do grafo corresponde a \"$term\"."
        val max = limit.coerceIn(1, 12)
        return buildString {
            append("${minOf(max, matches.size)} de ${matches.size} nó(s) do grafo:\n")
            matches.take(max).forEach { n ->
                val edges = adjacency[n.id].orEmpty()
                append("• ").append(n.label)
                if (n.file.isNotBlank()) append("  [").append(n.file).append(']')
                append("  (conexões: ").append(edges.size)
                if (n.community.isNotBlank()) append(", comunidade ").append(n.community)
                append(")\n")
                edges.take(10).forEach { e ->
                    val other = all[e.other]
                    val name = other?.label ?: e.other
                    val where = other?.file?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
                    append("    ").append(if (e.outgoing) "→ " else "← ").append(e.relation).append(": ").append(name).append(where).append('\n')
                }
                if (edges.size > 10) append("    … +${edges.size - 10} conexões\n")
            }
        }
    }
}
