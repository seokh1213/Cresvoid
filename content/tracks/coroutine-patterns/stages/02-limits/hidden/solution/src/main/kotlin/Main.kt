fun main() {
    val scenario = Scenario()
    val output = System.out.bufferedWriter()
    for (line in generateSequence(::readlnOrNull)) {
        val printed = scenario.execute(line)
        if (printed.isEmpty()) continue
        printed.forEach { output.appendLine(it) }
        output.flush()
    }
}
