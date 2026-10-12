fun main() {
    val node = CoordinatingNode()
    val output = StringBuilder()
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        val request = line.trim(' ')
        if (request.isNotEmpty()) {
            node.handle(RequestLine(request)).forEach { output.append(it).append('\n') }
        }
    }
    print(output)
}
