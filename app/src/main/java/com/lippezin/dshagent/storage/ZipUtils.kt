package com.lippezin.dshagent.storage

object ZipUtils {
    const val MAX_ENTRIES = 4000
    const val MAX_TOTAL_BYTES = 120L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
    const val MAX_ZIP_FILE_BYTES = 80L * 1024 * 1024

    private val skipNames = setOf(
        "__MACOSX", ".git", "node_modules", ".gradle", ".idea", "__pycache__",
        ".venv", "venv", ".dart_tool", "Pods", ".DS_Store", "Thumbs.db"
    )

    private val textExtensions = setOf(
        "md", "txt", "csv", "tsv", "json", "jsonl", "xml", "html", "htm", "css", "scss", "less",
        "js", "jsx", "mjs", "cjs", "ts", "tsx", "kt", "kts", "java", "py", "rb", "go", "rs",
        "c", "h", "cpp", "hpp", "cc", "cs", "php", "swift", "sh", "bash", "bat", "ps1",
        "yml", "yaml", "toml", "ini", "cfg", "conf", "properties", "gradle", "sql", "log",
        "vue", "svelte", "dart", "lua", "r", "pl", "tex", "rst", "proto", "gitignore", "env.example"
    )

    private val textNames = setOf(
        "dockerfile", "makefile", "license", "readme", "gemfile", "procfile", "gradlew",
        ".gitignore", ".gitattributes", ".editorconfig", "cmakelists.txt"
    )

    private val keyNames = setOf(
        "package.json", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "pom.xml", "requirements.txt", "pyproject.toml", "cargo.toml", "go.mod", "androidmanifest.xml",
        "tsconfig.json", "makefile", "dockerfile", "pubspec.yaml", "composer.json", "gemfile",
        "agents.md", "claude.md", "gemini.md"
    )

    /** Devolve os segmentos seguros do caminho, ou null se for inválido (.., ':', nomes enormes). */
    fun cleanSegments(entryName: String): List<String>? {
        val segs = entryName.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
        if (segs.isEmpty()) return null
        if (segs.any { it == ".." || it.contains(':') || it.length > 120 }) return null
        return segs
    }

    fun shouldSkip(segments: List<String>): Boolean = segments.any { it in skipNames }

    fun safeFolderName(zipName: String): String {
        val base = zipName.substringBeforeLast('.', zipName)
        val cleaned = base.map { ch -> if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-' || ch == ' ') ch else '_' }
            .joinToString("")
            .trim()
            .trim('.')
            .take(60)
        return cleaned.ifBlank { "upload" }
    }

    fun isTextName(name: String): Boolean {
        val lower = name.lowercase()
        if (lower in textNames) return true
        return lower.substringAfterLast('.', "") in textExtensions
    }

    fun isKeyFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower in keyNames || lower.startsWith("readme")
    }

    fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 8000)
        for (i in 0 until limit) if (bytes[i] == 0.toByte()) return true
        return false
    }
}
