fun main() {
    val input = System.`in`.bufferedReader()
    val header = input.readLine().split(' ')
    val service = FeedService(header[2].toInt(), parseMode(header[1]))
    val output = System.out.bufferedWriter()
    input.forEachLine { line ->
        output.write(service.execute(parseCommand(line)))
        output.newLine()
    }
    output.flush()
}
