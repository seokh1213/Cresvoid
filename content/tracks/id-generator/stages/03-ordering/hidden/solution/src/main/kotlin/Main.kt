fun main() {
    val nodes = Nodes()
    val shards = Shards()
    val ordering = Ordering()
    val whitespace = Regex("[ \\t]+")
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) {
            val tokens = trimmed.split(whitespace)
            val result = when (tokens[0]) {
                "CONFIG", "NODE", "NEXT", "DECODE" -> nodes.execute(tokens)
                "SHARD_CONFIG", "PLACE", "MOVE", "SHARD_NEXT", "SHARD_DECODE", "LOCATE" ->
                    shards.execute(tokens)
                else -> ordering.execute(tokens, nodes.settings)
            }
            output.appendLine(result)
        }
    }
    output.flush()
}
