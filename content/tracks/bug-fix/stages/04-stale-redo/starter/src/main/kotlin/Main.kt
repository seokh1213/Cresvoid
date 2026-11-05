import java.io.BufferedReader

sealed interface EditorRequest {
    data class MoveCursor(val position: Int) : EditorRequest
    data class Write(val text: String) : EditorRequest
    data class Erase(val count: Int) : EditorRequest
    data class Apply(val requests: List<EditorRequest>) : EditorRequest
    data object Back : EditorRequest
    data object Again : EditorRequest
    data object View : EditorRequest
}

private fun parseRequest(line: String, input: BufferedReader): EditorRequest {
    val separator = line.indexOf(' ')
    val verb = if (separator < 0) line else line.substring(0, separator)
    val argument = if (separator < 0) "" else line.substring(separator + 1)
    return when (verb) {
        "cursor" -> EditorRequest.MoveCursor(argument.toInt())
        "write" -> EditorRequest.Write(argument)
        "erase" -> EditorRequest.Erase(argument.toInt())
        "apply" -> EditorRequest.Apply(List(argument.toInt()) {
            parseRequest(checkNotNull(input.readLine()), input)
        })
        "back" -> EditorRequest.Back
        "again" -> EditorRequest.Again
        "view" -> EditorRequest.View
        else -> error("Unknown editor request")
    }
}

fun main() {
    val editor = EditingSession()
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    while (true) {
        val line = input.readLine() ?: break
        val response = editor.execute(parseRequest(line, input)) ?: continue
        output.write(response)
        output.newLine()
    }
    output.flush()
}
