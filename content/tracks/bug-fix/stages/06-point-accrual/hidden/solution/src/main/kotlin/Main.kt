fun main() {
    val service = PointService()
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().useLines { lines ->
        for (line in lines) {
            if (line.isBlank()) continue
            val command = CommandParser.parse(line)
            val result = if (command == null) {
                "ERROR invalid"
            } else {
                service.execute(command)
            }
            output.appendLine(result)
        }
    }
    output.flush()
}
