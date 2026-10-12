fun main() {
    val input = LineReader(System.`in`)
    val output = System.out.bufferedWriter()
    val engine = Engine { line ->
        output.write(line)
        output.write("\n")
    }
    while (true) {
        val command = input.readLine()?.trim(' ') ?: break
        when {
            command.isEmpty() -> continue
            opensRowBlock(command) -> {
                val block = engine.startBlock(command)
                generateSequence(input::readLine)
                    .map { it.trim(' ') }
                    .takeWhile { it != BLOCK_END }
                    .filter { it.isNotEmpty() }
                    .forEach(block::accept)
                engine.finish(block)
            }
            else -> engine.execute(command)
        }
    }
    output.flush()
}
