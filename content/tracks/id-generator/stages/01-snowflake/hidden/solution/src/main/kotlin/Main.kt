fun main() {
    val nodes = Nodes()
    val whitespace = Regex("[ \\t]+")
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) {
            output.appendLine(nodes.execute(trimmed.split(whitespace)))
        }
    }
    output.flush()
}
