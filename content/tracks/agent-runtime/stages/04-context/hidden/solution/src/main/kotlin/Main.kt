fun main() {
    val transcript = Transcript()
    val runtime = AgentRuntime(transcript)
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        when (val parsed = parseCommandLine(line)) {
            ParsedLine.Blank -> Unit
            is ParsedLine.Invalid -> transcript.error(parsed.error)
            is ParsedLine.Valid -> runtime.execute(parsed.command)
        }
    }
    // print 는 실행 환경의 기본 인코딩으로 쓰므로 UTF-8 바이트를 직접 쓴다
    System.out.write(transcript.toUtf8())
    System.out.flush()
}
