import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val project = File(args.single()).canonicalFile
val process = ProcessBuilder("sh", "run.sh").directory(project)
    .redirectError(ProcessBuilder.Redirect.DISCARD).start()
fun stopServer() {
    val descendants = process.toHandle().descendants().toList()
    descendants.asReversed().forEach { it.destroyForcibly() }
    process.destroyForcibly()
    descendants.forEach { runCatching { it.onExit().get(2, TimeUnit.SECONDS) } }
    process.waitFor(2, TimeUnit.SECONDS)
}
val cleanup = Thread { stopServer() }
Runtime.getRuntime().addShutdownHook(cleanup)
val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
val executor = Executors.newSingleThreadExecutor { task -> Thread(task).apply { isDaemon = true } }
val metadata = """"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}"""
fun exchange(request: String, expected: String) {
    writer.write(request)
    writer.newLine()
    writer.flush()
    val response = executor.submit<String?> { reader.readLine() }
    check(response.get(10, TimeUnit.SECONDS) == expected) { "Interactive response mismatch" }
}
try {
    exchange(
        """{"jsonrpc":"2.0","id":"live","method":"tools/call","params":{$metadata,"name":"product_get","arguments":{"productId":"p-100"}}}""",
        """{"id":"live","jsonrpc":"2.0","result":{"content":[{"text":"id=p-100\nname=머그 \"밤\"\nprice=12000\nstock=8","type":"text"}],"isError":false,"resultType":"complete"}}""",
    )
    writer.write("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"unknown"}}""")
    writer.newLine()
    exchange(
        """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""",
        """{"error":{"code":-32602,"message":"Invalid params"},"id":2,"jsonrpc":"2.0"}""",
    )
    exchange(
        """{"jsonrpc":"2.0","id":"after-error","method":"tools/call","params":{$metadata,"name":"product_get","arguments":{"productId":"p-999"}}}""",
        """{"id":"after-error","jsonrpc":"2.0","result":{"content":[{"text":"UNKNOWN_PRODUCT p-999","type":"text"}],"isError":true,"resultType":"complete"}}""",
    )
    writer.close()
    check(process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0)
    check(reader.readLine() == null)
} finally {
    stopServer()
    executor.shutdownNow()
    reader.close()
    writer.close()
    Runtime.getRuntime().removeShutdownHook(cleanup)
}
