fun main() {
    val engine = SearchEngine()
    val output = StringBuilder()
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        engine.execute(line)?.let { output.append(it).append('\n') }
    }
    // print 는 실행 환경의 기본 인코딩으로 쓰므로 UTF-8 바이트를 직접 쓴다
    System.out.write(output.toString().encodeToByteArray())
    System.out.flush()
}
