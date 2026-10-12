package cresvoid.command

import cresvoid.terminal.Console
import cresvoid.terminal.Terminal
import java.nio.file.Files

internal fun Terminal.pageText(text: String): Boolean {
    val file = Files.createTempFile("cresvoid-", ".txt")
    return try {
        Files.writeString(file, text)
        page(file)
    } finally {
        Files.deleteIfExists(file)
    }
}

internal fun showText(console: Console, terminal: Terminal, text: String) {
    if (terminal.stdoutIsTerminal && terminal.pageText(text)) return
    console.println()
    console.print(text)
}
