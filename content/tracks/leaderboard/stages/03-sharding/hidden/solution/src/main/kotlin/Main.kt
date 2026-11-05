fun main() {
    val service = Service()
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    input.forEachLine { raw ->
        val line = raw.trim(' ', '\t')
        if (line.isNotEmpty()) {
            val response = service.execute(parseCommand(line))
            if (response != null) {
                output.write(response)
                output.newLine()
            }
        }
    }
    output.flush()
}
