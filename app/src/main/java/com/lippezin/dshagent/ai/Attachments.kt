package com.lippezin.dshagent.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lippezin.dshagent.storage.ZipUtils

object Attachments {
    enum class Kind { IMAGE, VIDEO, AUDIO, ZIP, PDF, TEXT, OTHER }

    fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "anexo")
    }

    fun kindOf(name: String, mime: String?): Kind {
        val ext = name.substringAfterLast('.', "").lowercase()
        val m = mime.orEmpty().lowercase()
        return when {
            ext == "zip" || m == "application/zip" || m == "application/x-zip-compressed" || m == "application/x-zip" -> Kind.ZIP
            ext == "pdf" || m == "application/pdf" -> Kind.PDF
            m.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") -> Kind.IMAGE
            m.startsWith("video/") || ext in setOf("mp4", "webm", "mkv", "mov", "3gp") -> Kind.VIDEO
            m.startsWith("audio/") || ext in setOf("mp3", "wav", "m4a", "ogg", "aac", "flac", "opus") -> Kind.AUDIO
            m.startsWith("text/") || ZipUtils.isTextName(name) -> Kind.TEXT
            else -> Kind.OTHER
        }
    }

    fun kindOfName(name: String): Kind = kindOf(name, null)
}
