fun main() {
    val output = Output(System.out.bufferedWriter())
    val session = Session(output)
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        session.handle(line)
    }
    output.flush()
}
