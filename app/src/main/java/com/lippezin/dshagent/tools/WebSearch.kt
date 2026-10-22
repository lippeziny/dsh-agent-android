package com.lippezin.dshagent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

object WebSearch {
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Consulta de pesquisa vazia." }
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query.take(240), "UTF-8")
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Android; DSH-Agent/0.2)")
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

    private fun assertPublicHost(url: String) {
        val host = URL(url).host
        val addresses = InetAddress.getAllByName(host)
        if (addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress }) {
            throw IllegalArgumentException("Endereços locais ou privados não são permitidos.")
        }
    }

    /** Baixa uma página HTTPS pública e devolve o texto principal (sem scripts/menus). */
    suspend fun fetch(rawUrl: String): String = withContext(Dispatchers.IO) {
        var url = rawUrl.trim()
        require(url.startsWith("https://")) { "Use uma URL https:// completa." }
        for (hop in 0..4) {
            assertPublicHost(url)
            val response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android; DSH-Agent/0.2)")
                .timeout(20_000)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize(2_000_000)
                .execute()
            val code = response.statusCode()
            if (code in 300..399) {
                val location = response.header("Location") ?: throw IllegalStateException("Redirecionamento sem destino.")
                url = URL(URL(url), location).toString()
                require(url.startsWith("https://")) { "Redirecionamento para endereço não HTTPS bloqueado." }
                continue
            }
            if (code !in 200..299) throw IllegalStateException("HTTP $code ao abrir a página.")
            val doc = response.parse()
            doc.select("script,style,noscript,nav,footer,header,aside,form,svg").remove()
            val text = doc.body().text()
            return@withContext "Título: ${doc.title()}\nURL: $url\n\n${text.take(14_000)}"
        }
        throw IllegalStateException("Redirecionamentos demais.")
    }
}
