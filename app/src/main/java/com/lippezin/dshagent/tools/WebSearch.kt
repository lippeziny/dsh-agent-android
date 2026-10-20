package com.lippezin.dshagent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

object WebSearch {
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Consulta de pesquisa vazia." }
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query.take(240), "UTF-8")
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Android; DSH-Agent/0.1)")
            .timeout(15_000)
            .get()
        val results = doc.select(".result").take(6).mapNotNull { node ->
            val title = node.selectFirst("a.result__a") ?: return@mapNotNull null
            val snippet = node.selectFirst(".result__snippet")?.text().orEmpty()
            val link = title.absUrl("href").ifBlank { title.attr("href") }
            "- ${title.text()}\n  $link\n  $snippet"
        }
        if (results.isEmpty()) "A pesquisa não retornou resultados extraíveis. Tente reformular a consulta." else results.joinToString("\n\n")
    }
}
