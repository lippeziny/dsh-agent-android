package com.lippezin.dshagent.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.lippezin.dshagent.data.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MediaProcessor(private val context: Context) {
    data class PreparedMedia(val content: JSONArray, val names: List<String>, val extraText: String)

    /** Aqui chegam anexos que não são ZIP (ZIPs são tratados pelo ProjectStorage). */
    suspend fun prepare(providers: List<ProviderConfig>, uris: List<Uri>): PreparedMedia = withContext(Dispatchers.IO) {
        val mediaParts = JSONArray()
        val names = mutableListOf<String>()
        val notes = mutableListOf<String>()
        uris.take(8).forEach { uri ->
            val name = Attachments.displayName(context, uri)
            val mime = context.contentResolver.getType(uri).orEmpty()
            names += name
            when (Attachments.kindOf(name, mime)) {
                Attachments.Kind.IMAGE -> {
                    val image = runCatching { imageDataUrl(uri) }.getOrNull()
                    if (image != null) mediaParts.put(imagePart(image)) else notes += "Não consegui ler a imagem `$name`."
                }
                Attachments.Kind.VIDEO -> {
                    val frames = runCatching { videoFrames(uri) }.getOrDefault(emptyList())
                    if (frames.isEmpty()) notes += "Vídeo anexado: $name (não foi possível extrair quadros)."
                    else {
                        notes += "Vídeo `$name`: foram extraídos ${frames.size} quadros representativos."
                        frames.forEach { mediaParts.put(imagePart(it)) }
                    }
                }
                Attachments.Kind.AUDIO -> {
                    val transcript = providers.asSequence().mapNotNull { candidate ->
                        runCatching { transcribe(candidate, uri, name) }.getOrNull()?.takeIf { it.isNotBlank() }
                    }.firstOrNull()
                    if (!transcript.isNullOrBlank()) notes += "Transcrição do áudio `$name`:\n$transcript"
                    else notes += "Áudio anexado: $name. Nenhum dos provedores configurados retornou uma transcrição compatível em /audio/transcriptions; envie o texto transcrito."
                }
                Attachments.Kind.PDF -> {
                    val pages = runCatching { pdfPages(uri) }.getOrDefault(emptyList())
                    if (pages.isEmpty()) notes += "PDF anexado: $name (não foi possível renderizar as páginas)."
                    else {
                        notes += "PDF `$name`: as primeiras ${pages.size} páginas foram renderizadas como imagens (não há extração de texto)."
                        pages.forEach { mediaParts.put(imagePart(it)) }
                    }
                }
                Attachments.Kind.TEXT -> {
                    val text = runCatching { readSmallText(uri) }.getOrNull()
                    if (!text.isNullOrBlank()) notes += "Conteúdo do arquivo `$name`:\n${text.take(60_000)}"
                    else notes += "Arquivo de texto anexado: $name (vazio ou não legível)."
                }
                else -> notes += "Arquivo anexado: $name (tipo ${mime.ifBlank { "desconhecido" }}; conteúdo binário não é interpretado diretamente)."
            }
        }
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", "Os anexos deste pedido são: ${names.joinToString(", ")}."))
        for (i in 0 until mediaParts.length()) content.put(mediaParts.getJSONObject(i))
        PreparedMedia(content, names, notes.joinToString("\n\n"))
    }

    private fun imagePart(dataUrl: String): JSONObject = JSONObject()
        .put("type", "image_url")
        .put("image_url", JSONObject().put("url", dataUrl).put("detail", "auto"))

    private fun readSmallText(uri: Uri): String {
        val bytes = readBounded(uri, 1_000_001) ?: return ""
        require(bytes.size <= 1_000_000) { "Arquivo de texto excede 1 MB." }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readBounded(uri: Uri, limit: Int): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (out.size() < limit) {
                val count = stream.read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (count <= 0) break
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }
    }

    private fun imageDataUrl(uri: Uri): String {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        var sample = 1
        while (options.outWidth / sample > 1600 || options.outHeight / sample > 1600) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Imagem inválida")
        return bitmap.toJpegDataUrl()
    }

    private fun videoFrames(uri: Uri): List<String> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val positions = if (durationMs > 2_000L) listOf(0L, durationMs / 2, (durationMs - 500).coerceAtLeast(0)) else listOf(0L)
            return positions.distinct().mapNotNull { ms ->
                val bitmap = retriever.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@mapNotNull null
                bitmap.toJpegDataUrl()
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun pdfPages(uri: Uri, maxPages: Int = 4): List<String> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = minOf(renderer.pageCount, maxPages)
                val pages = ArrayList<String>()
                for (index in 0 until count) {
                    renderer.openPage(index).use { page ->
                        val scale = 1400f / maxOf(page.width, page.height)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages += bitmap.toJpegDataUrl()
                    }
                }
                return pages
            }
        }
    }

    private fun Bitmap.toJpegDataUrl(): String {
        val maxSide = 1440
        val factor = maxOf(width, height).toFloat() / maxSide
        val scaled = if (factor > 1f) Bitmap.createScaledBitmap(this, (width / factor).toInt().coerceAtLeast(1), (height / factor).toInt().coerceAtLeast(1), true) else this
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, stream)
        if (scaled !== this) scaled.recycle()
        val bytes = stream.toByteArray()
        if (!isRecycled) recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun transcribe(provider: ProviderConfig, uri: Uri, name: String): String {
        val bytes = readBounded(uri, 24 * 1024 * 1024 + 1) ?: return ""
        if (bytes.size > 24 * 1024 * 1024) return "O arquivo excede o limite de 24 MB desta transcrição."
        val boundary = "----DSH${UUID.randomUUID().toString().replace("-", "")}"
        val endpoint = URL("${provider.baseUrl.trimEnd('/')}/audio/transcriptions")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 40_000
            doOutput = true
            if (provider.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${provider.apiKey.trim()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        conn.outputStream.use { out ->
            fun field(key: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", "whisper-1")
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${name.replace("\"", "_")}\"\r\nContent-Type: ${context.contentResolver.getType(uri) ?: "application/octet-stream"}\r\n\r\n".toByteArray())
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val status = conn.responseCode
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) return ""
        return JSONObject(body).optString("text", "")
    }
}
