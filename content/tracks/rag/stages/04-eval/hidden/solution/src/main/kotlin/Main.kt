fun main() {
    val application = RagApplication()
    val output = System.out.bufferedWriter(Charsets.UTF_8)
    System.`in`.bufferedReader(Charsets.UTF_8).useLines { lines ->
        for (line in lines) {
            if (line.isEmpty()) continue
            val rows = try {
                application.execute(CommandParser(line.split('\t')).parse()).ifEmpty { listOf("(none)") }
            } catch (failure: CommandFailure) {
                listOf("ERR ${failure.reason}")
            }
            for (row in rows) { output.write(row); output.newLine() }
        }
    }
    output.flush()
}
