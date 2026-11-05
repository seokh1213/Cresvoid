import java.io.BufferedReader

fun BufferedReader.forEachMessage(process: (String) -> Unit) {
    val line = StringBuilder()
    while (true) {
        val character = read()
        if (character == -1) break
        // CR remains JSON whitespace; LF also handles the end of a CRLF message.
        if (character == '\n'.code) {
            process(line.toString())
            line.setLength(0)
        } else {
            line.append(character.toChar())
        }
    }
    if (line.isNotEmpty()) process(line.toString())
}
