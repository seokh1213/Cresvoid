fun main() {
    val parser = CommandParser()
    val service = LeaseService(LockStore())
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    input.forEachLine { line ->
        val reply = service.execute(parser.parse(line))
        if (reply != null) {
            output.write(reply)
            output.newLine()
        }
    }
    output.flush()
}
