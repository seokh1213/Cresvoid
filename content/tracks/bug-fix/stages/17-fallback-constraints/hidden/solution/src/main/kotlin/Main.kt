fun main() {
    val service = PlacementService()
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().useLines { commands ->
        for (line in commands) {
            if (line.isBlank()) continue
            val request = CommandParser.parse(line)
            val result = if (request == null) "ERROR" else service.place(request)
            output.write(result)
            output.newLine()
        }
    }
    output.flush()
}
