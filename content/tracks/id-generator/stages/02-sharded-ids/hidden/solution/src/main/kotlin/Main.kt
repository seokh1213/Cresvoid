fun main() {
    val nodes = Nodes()
    val shards = Shards()
    val whitespace = Regex("[ \\t]+")
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) {
            val tokens = trimmed.split(whitespace)
            val result = when (tokens[0]) {
                "CONFIG", "NODE", "NEXT", "DECODE" -> nodes.execute(tokens)
                else -> shards.execute(tokens)
            }
            output.appendLine(result)
        }
    }
    output.flush()
}
