private const val COPY_END = "\\."

fun main() {
    val input = LineReader(System.`in`)
    val output = System.out.bufferedWriter()
    val database = Database { line -> output.append(line).append('\n') }
    while (true) {
        val command = input.readLine()?.trim(' ') ?: break
        when {
            command.isEmpty() -> continue
            command.substringBefore(' ') == "COPY" -> {
                val copy = database.beginCopy(command)
                generateSequence(input::readLine).takeWhile { it != COPY_END }.forEach(copy::accept)
                database.finishCopy(copy)
            }
            else -> database.execute(command)
        }
    }
    output.flush()
}
