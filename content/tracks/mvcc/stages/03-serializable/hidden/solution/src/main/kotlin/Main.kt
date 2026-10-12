fun main() {
    val output = Output()
    val engine = TransactionEngine(output)
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        val trimmed = line.trim(' ')
        if (trimmed.isNotEmpty()) engine.handle(trimmed)
    }
    output.writeTo(System.out)
}
