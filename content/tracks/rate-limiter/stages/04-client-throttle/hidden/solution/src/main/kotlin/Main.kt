fun main() {
    val output = System.out.bufferedWriter()
    val service = RateLimitService { line -> output.append(line).append('\n') }
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        val request = line.trim(' ')
        if (request.isNotEmpty()) service.handle(request)
    }
    service.finishInput()
    output.flush()
}
