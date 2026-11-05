package cresvoid.terminal

import java.io.File
import java.io.IOException
import java.io.PrintStream

data class ScreenSize(val rows: Int, val columns: Int)

/** A full-screen view that redraws whole frames and reads single keys. */
interface Screen : AutoCloseable {
    fun size(): ScreenSize

    fun draw(lines: List<String>)

    fun readKey(): Key
}

/**
 * Full-screen mode on the controlling terminal using only stty and ANSI sequences, so the engine keeps its JDK-only
 * runtime. The terminal settings are restored on close and from a shutdown hook if the JVM exits early.
 */
class TtyScreen private constructor(
    private val saved: String,
    private val out: PrintStream,
) : Screen {
    private val keys = KeyReader(System.`in`)
    private val restoreHook = Thread { restore() }
    private var closed = false

    init {
        Runtime.getRuntime().addShutdownHook(restoreHook)
        out.print("$ESC[?1049h$ESC[?25l")
        out.flush()
    }

    override fun size(): ScreenSize {
        val reported = stty("size")?.trim()?.split(' ')?.mapNotNull(String::toIntOrNull)
        return if (reported != null && reported.size == 2 && reported.all { it > 0 }) ScreenSize(reported[0], reported[1]) else ScreenSize(24, 80)
    }

    override fun draw(lines: List<String>) {
        val frame = StringBuilder("$ESC[H")
        lines.forEachIndexed { index, line ->
            frame.append(line).append("$ESC[0m$ESC[K")
            if (index < lines.lastIndex) frame.append("\r\n")
        }
        frame.append("$ESC[J")
        out.print(frame)
        out.flush()
    }

    override fun readKey(): Key = keys.next()

    override fun close() {
        restore()
        try {
            Runtime.getRuntime().removeShutdownHook(restoreHook)
        } catch (_: IllegalStateException) {
        }
    }

    @Synchronized
    private fun restore() {
        if (closed) return
        closed = true
        out.print("$ESC[?25h$ESC[?1049l")
        out.flush()
        stty(saved)
    }

    companion object {
        private const val ESC = "\u001b"

        fun open(out: PrintStream = System.out): TtyScreen? {
            val saved = stty("-g")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // Keep output processing on so "\n" still returns to column 0; -isig turns Ctrl-C into a key.
            stty("-icanon", "-echo", "-isig", "min", "1", "time", "0") ?: return null
            return TtyScreen(saved, out)
        }

        private fun stty(vararg arguments: String): String? = try {
            val process = ProcessBuilder(listOf("stty") + arguments)
                .redirectInput(File("/dev/tty"))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) output else null
        } catch (_: IOException) {
            null
        }
    }
}
