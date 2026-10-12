package cresvoid.terminal

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

interface Terminal {
    val stdinIsTerminal: Boolean
    val stdoutIsTerminal: Boolean

    fun page(file: Path): Boolean

    fun readLine(): String?

    fun answers(): AnswerSource
}

interface AnswerSource : AutoCloseable {
    fun nextLine(): String?
}

internal object TerminalProbe {
    val stdinIsTerminal: Boolean by lazy { inheritsTerminal(descriptor = 0) }
    val stdoutIsTerminal: Boolean by lazy { inheritsTerminal(descriptor = 1) }

    private fun inheritsTerminal(descriptor: Int): Boolean {
        val probe = ProcessBuilder("sh", "-c", "test -t $descriptor")
            .redirectInput(if (descriptor == 0) ProcessBuilder.Redirect.INHERIT else ProcessBuilder.Redirect.from(File("/dev/null")))
            .redirectOutput(if (descriptor == 1) ProcessBuilder.Redirect.INHERIT else ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        return try {
            probe.start().waitFor() == 0
        } catch (_: IOException) {
            false
        }
    }
}

class SystemTerminal : Terminal {
    override val stdinIsTerminal: Boolean get() = TerminalProbe.stdinIsTerminal
    override val stdoutIsTerminal: Boolean get() = TerminalProbe.stdoutIsTerminal

    override fun page(file: Path): Boolean {
        val pager = System.getenv("PAGER").orEmpty().ifEmpty { "less" }
        val charset = if (System.getenv("LESSCHARSET").isNullOrEmpty()) mapOf("LESSCHARSET" to "utf-8") else emptyMap()
        return try {
            runInteractive(listOf("sh", "-c", "$pager \"\$1\"", "sh", file.toString()), charset) == 0
        } catch (_: IOException) {
            false
        }
    }

    override fun readLine(): String? = readlnOrNull()

    override fun answers(): AnswerSource = InterruptibleAnswers()

    private fun runInteractive(command: List<String>, extraEnvironment: Map<String, String>): Int {
        val previous = handleSignal("INT") { }
        try {
            val builder = ProcessBuilder(command).inheritIO()
            builder.environment().putAll(extraEnvironment)
            return builder.start().waitFor()
        } finally {
            restoreSignal("INT", previous)
        }
    }
}

private sealed interface Reply {
    data class Line(val text: String) : Reply

    data object Abort : Reply
}

private class InterruptibleAnswers : AnswerSource {
    private val replies = LinkedBlockingQueue<Reply>()
    private val previousInterruptHandler = handleSignal("INT") { replies.put(Reply.Abort) }

    init {
        thread(isDaemon = true, name = "cresvoid-answers") { readLines() }
    }

    override fun nextLine(): String? = (replies.take() as? Reply.Line)?.text

    private fun readLines() {
        val line = ByteArrayOutputStream()
        try {
            val input = System.`in`.buffered()
            while (true) {
                val byte = input.read()
                if (byte < 0) break
                line.write(byte)
                if (byte == '\n'.code) {
                    replies.put(Reply.Line(line.toString(Charsets.UTF_8)))
                    line.reset()
                }
            }
            if (line.size() > 0) replies.put(Reply.Line(line.toString(Charsets.UTF_8)))
        } catch (_: IOException) {
        }
        replies.put(Reply.Abort)
    }

    override fun close() {
        restoreSignal("INT", previousInterruptHandler)
    }
}
