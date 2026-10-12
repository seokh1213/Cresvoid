fun main() {
    val output = System.out.bufferedWriter()
    val broker = Broker { line -> output.append(line).append('\n') }
    val input = System.`in`.readBytes().decodeToString()
    for (rawLine in input.splitToSequence('\n')) {
        val line = rawLine.trim(' ')
        if (line.isNotEmpty()) broker.handle(line)
    }
    output.flush()
}
