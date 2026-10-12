fun main() {
    val output = System.out.bufferedWriter()
    val namenode = Namenode { line -> output.append(line).append('\n') }
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        val request = line.trim(' ')
        if (request.isNotEmpty()) namenode.handle(request)
    }
    output.flush()
}
