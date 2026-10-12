fun main() {
    val output = System.out.bufferedWriter()
    val simulator = Simulator { line -> output.append(line).append('\n') }
    for (rawLine in System.`in`.readBytes().decodeToString().split('\n')) {
        val line = rawLine.trim(' ')
        if (line.isNotEmpty()) simulator.handle(line)
    }
    output.flush()
}
