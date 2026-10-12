package cresvoid.content

import cresvoid.project.readUtf8
import java.nio.file.Path

private val paragraphMarker = Regex("""^\[(\d+(?:\.\d+)*)][\t\n\f\r ]""")

class Document(val path: Path, val paragraphs: Map<String, String>, val duplicates: List<String>)

fun loadDocument(path: Path): Document = parseDocument(path, path.readUtf8())

fun parseDocument(path: Path, text: String): Document {
    val paragraphs = LinkedHashMap<String, String>()
    val duplicates = mutableListOf<String>()
    var currentId: String? = null
    val currentLines = mutableListOf<String>()
    fun closeParagraph() {
        val id = currentId ?: return
        if (id in paragraphs) duplicates += id else paragraphs[id] = currentLines.joinToString("\n")
        currentId = null
        currentLines.clear()
    }
    var inFence = false
    for (rawLine in text.split("\n")) {
        val line = rawLine.trimEnd('\r')
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            closeParagraph()
            inFence = !inFence
            continue
        }
        if (inFence) continue
        val marker = paragraphMarker.find(line)
        if (marker != null) {
            closeParagraph()
            currentId = marker.groupValues[1]
            currentLines += line.substring(marker.value.length).trim()
            continue
        }
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            closeParagraph()
            continue
        }
        if (currentId != null) currentLines += trimmed
    }
    closeParagraph()
    return Document(path, paragraphs, duplicates)
}

fun containsQuote(passages: List<String>, quote: String): Boolean {
    val needle = normalizeForMatch(quote)
    return passages.any { normalizeForMatch(it).contains(needle) }
}

fun normalizeForMatch(text: String): String {
    val withoutMarkup = buildString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("`", index) -> index += 1
                text.startsWith("**", index) -> index += 2
                else -> append(text[index++])
            }
        }
    }
    return collapseSpaces(withoutMarkup)
}

private val spaces = Regex("""(?U)\s+""")

fun collapseSpaces(text: String): String = text.split(spaces).filter { it.isNotEmpty() }.joinToString(" ")
