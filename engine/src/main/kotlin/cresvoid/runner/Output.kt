package cresvoid.runner

fun normalizeOutput(raw: ByteArray): List<String> = normalizeLines(String(raw, Charsets.UTF_8).split("\n"))

fun normalizeLines(lines: List<String>): List<String> =
    lines.map { it.trimEnd(' ', '\t', '\r') }.dropLastWhile { it.isEmpty() }

fun joinInput(lines: List<String>): ByteArray =
    if (lines.isEmpty()) ByteArray(0) else (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)

fun tailLines(raw: ByteArray, count: Int): List<String> = normalizeOutput(raw).takeLast(count)

fun firstDifference(expected: List<String>, actual: List<String>): Int? {
    val shorter = minOf(expected.size, actual.size)
    val mismatch = (0 until shorter).firstOrNull { expected[it] != actual[it] }
    return mismatch ?: shorter.takeIf { expected.size != actual.size }
}
