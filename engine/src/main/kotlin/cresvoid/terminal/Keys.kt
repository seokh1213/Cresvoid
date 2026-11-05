package cresvoid.terminal

import java.io.InputStream

sealed interface Key {
    data class Text(val value: String) : Key

    data object Up : Key

    data object Down : Key

    data object Left : Key

    data object Right : Key

    data object PageUp : Key

    data object PageDown : Key

    data object Home : Key

    data object End : Key

    data object Enter : Key

    data object Backspace : Key

    data object Tab : Key

    data object BackTab : Key

    data object Escape : Key

    data object Interrupt : Key

    data object EndOfInput : Key
}

/** Decodes keys from a terminal in non-canonical mode: UTF-8 text, control bytes, and CSI or SS3 sequences. */
class KeyReader(
    private val input: InputStream,
    private val escapeWaitMillis: Long = 40,
) {
    fun next(): Key {
        while (true) {
            val byte = input.read()
            decode(byte)?.let { return it }
        }
    }

    private fun decode(byte: Int): Key? = when {
        byte < 0 || byte == CTRL_D -> Key.EndOfInput
        byte == CTRL_C -> Key.Interrupt
        byte == '\r'.code || byte == '\n'.code -> Key.Enter
        byte == DELETE || byte == '\b'.code -> Key.Backspace
        byte == '\t'.code -> Key.Tab
        byte == ESCAPE -> escape()
        byte < 0x20 -> null
        else -> Key.Text(utf8(byte))
    }

    private fun escape(): Key? {
        // A lone Esc and the start of an arrow key arrive as the same byte; only the follow-up bytes tell them apart.
        if (!waitForMore()) return Key.Escape
        val introducer = input.read()
        if (introducer != '['.code && introducer != 'O'.code) return Key.Escape
        val parameters = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return Key.EndOfInput
            if (byte in 0x40..0x7E) return sequence(parameters.toString(), byte.toChar())
            parameters.append(byte.toChar())
        }
    }

    private fun sequence(parameters: String, final: Char): Key? = when (final) {
        'A' -> Key.Up
        'B' -> Key.Down
        'C' -> Key.Right
        'D' -> Key.Left
        'H' -> Key.Home
        'F' -> Key.End
        'Z' -> Key.BackTab
        '~' -> when (parameters.substringBefore(';')) {
            "1", "7" -> Key.Home
            "4", "8" -> Key.End
            "5" -> Key.PageUp
            "6" -> Key.PageDown
            else -> null
        }
        else -> null
    }

    private fun waitForMore(): Boolean {
        val deadline = System.nanoTime() + escapeWaitMillis * 1_000_000
        while (input.available() == 0) {
            if (System.nanoTime() > deadline) return false
            Thread.sleep(2)
        }
        return true
    }

    private fun utf8(first: Int): String {
        val length = when {
            first and 0x80 == 0 -> 1
            first and 0xE0 == 0xC0 -> 2
            first and 0xF0 == 0xE0 -> 3
            first and 0xF8 == 0xF0 -> 4
            else -> 1
        }
        val bytes = ByteArray(length)
        bytes[0] = first.toByte()
        for (index in 1 until length) {
            val next = input.read()
            if (next < 0) return String(bytes, 0, index, Charsets.UTF_8)
            bytes[index] = next.toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val CTRL_C = 3
        const val CTRL_D = 4
        const val ESCAPE = 27
        const val DELETE = 127
    }
}
