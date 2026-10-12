package cresvoid.content

import cresvoid.project.readUtf8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

private val codeLocationPattern = Regex("""(.+):([0-9]+)(?:-([0-9]+))?""")

data class CodeLocation(val file: String, val firstLine: Int, val lastLine: Int) {
    val label: String
        get() = if (firstLine == lastLine) "$file ${firstLine}행" else "$file $firstLine~${lastLine}행"

    companion object {
        fun parse(ref: String): CodeLocation? {
            val match = codeLocationPattern.matchEntire(ref) ?: return null
            val (file, first, last) = match.destructured
            val firstLine = first.toIntOrNull() ?: return null
            val lastLine = last.ifEmpty { first }.toIntOrNull() ?: return null
            return CodeLocation(file, firstLine, lastLine)
        }
    }
}

fun isCodeLocation(ref: String): Boolean = ':' in ref

class CodeSources(projectDir: Path) {
    val root: Path = kotlinSourceRoot(projectDir).normalize()
    private val linesByFile = mutableMapOf<String, List<String>?>()

    fun lines(file: String): List<String>? = linesByFile.getOrPut(file) {
        val path = root.resolve(file).normalize()
        if (path.startsWith(root) && path.isRegularFile()) sourceLines(path) else null
    }
}

fun kotlinSourceRoot(projectDir: Path): Path = projectDir.resolve("src").resolve("main").resolve("kotlin")

fun kotlinSourceFiles(projectDir: Path): List<String> {
    val root = kotlinSourceRoot(projectDir)
    if (!root.isDirectory()) return emptyList()
    return Files.walk(root).use { paths ->
        paths.filter { it.isRegularFile() && it.extension == "kt" }.map { root.relativize(it).toString() }.toList()
    }.sorted()
}

fun sourceLines(file: Path): List<String> {
    val text = file.readUtf8()
    if (text.isEmpty()) return emptyList()
    return text.removeSuffix("\n").split("\n").map { it.removeSuffix("\r") }
}

fun numberedSources(projectDir: Path): String {
    val root = kotlinSourceRoot(projectDir)
    return kotlinSourceFiles(projectDir).joinToString("\n") { file -> numberedFile(file, sourceLines(root.resolve(file))) }
}

private fun numberedFile(name: String, lines: List<String>): String = buildString {
    val width = lines.size.toString().length
    append("==> ").append(name).append(" <==\n")
    lines.forEachIndexed { index, line ->
        append((index + 1).toString().padStart(width))
        if (line.isNotEmpty()) append("  ").append(line)
        append('\n')
    }
}
