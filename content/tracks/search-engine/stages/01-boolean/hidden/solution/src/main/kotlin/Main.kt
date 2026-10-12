fun main() {
    val engine = SearchEngine()
    val output = StringBuilder()
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        engine.execute(line)?.let { output.append(it).append('\n') }
    }
    print(output)
}
