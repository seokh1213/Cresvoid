import java.io.BufferedReader

fun main() {
    val reader = System.`in`.bufferedReader(Charsets.UTF_8)
    while (true) {
        val command = reader.readPhysicalLine() ?: break
        when (val request = reader.readImport(command)) {
            is ImportRequest.Ready -> println(importProducts(request.csv).render())
            ImportRequest.BadCommand -> println("ERROR COMMAND")
            ImportRequest.Incomplete -> {
                println("ERROR INCOMPLETE")
                return
            }
        }
    }
}

private sealed interface ImportRequest {
    data class Ready(val csv: String) : ImportRequest
    data object BadCommand : ImportRequest
    data object Incomplete : ImportRequest
}

private fun BufferedReader.readImport(command: String): ImportRequest {
    val parts = command.trim().split(" ")
    if (parts.size != 2 || parts[0] != "IMPORT") {
        return ImportRequest.BadCommand
    }
    val lineCount = parts[1].toIntOrNull()
    if (lineCount == null || lineCount < 1) {
        return ImportRequest.BadCommand
    }
    return readCsvBody(lineCount)
}

private fun BufferedReader.readCsvBody(lineCount: Int): ImportRequest {
    val csv = StringBuilder()
    repeat(lineCount) {
        val physicalLine = readPhysicalLine() ?: return ImportRequest.Incomplete
        csv.append(physicalLine)
    }
    return ImportRequest.Ready(csv.toString())
}

private fun BufferedReader.readPhysicalLine(): String? {
    val line = StringBuilder()
    while (true) {
        val next = read()
        if (next == -1) {
            return line.toString().takeIf { it.isNotEmpty() }
        }
        val character = next.toChar()
        line.append(character)
        if (character == '\n') return line.toString()
    }
}
