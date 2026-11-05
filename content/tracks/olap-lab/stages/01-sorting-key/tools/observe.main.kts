@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

require(args.size == 1) { "usage: kotlin observe.main.kts <learner-project> (from repository root)" }
val directory = File(args[0]).canonicalFile
require(directory.isDirectory) { "learner project does not exist" }
val compose = File("content/tracks/olap-lab/stages/01-sorting-key/hidden/compose.yaml")
require(compose.isFile) { "run from repository root" }
val build = ProcessBuilder(File(directory, "build.sh").absolutePath).directory(directory).inheritIO()
    .apply { environment().remove("KOTLIN_RUNNER") }.start()
check(build.waitFor() == 0) { "build failed" }
requireDocker()

fun response(client: HttpClient, request: HttpRequest): String {
    val result = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(result.statusCode() == 200) { "HTTP ${result.statusCode()}: ${result.body()}" }
    return result.body().trim()
}

HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
    ComposeStack(compose, "olap-sorting-observe").use { stack ->
        stack.up(180)
        val database = "http://127.0.0.1:${stack.port("clickhouse", 8123)}"
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        println("CLICKHOUSE_URL=$database PORT=$port")
        Program(directory, mapOf("PORT" to "$port", "CLICKHOUSE_URL" to database), base).use {
            val timeout = Duration.ofSeconds(90)
            println("POST /prepare: " + response(client, post(base, "/prepare", "", timeout)))
            println("예측을 기록한 뒤 Enter를 눌러라.")
            readlnOrNull()
            for (path in listOf("/observe", "/storage")) {
                println("curl -fsS '$base$path'")
                println(response(client, get(base, path, timeout)))
            }
            println("다른 터미널에서 추가로 관찰하거나 Enter 또는 Ctrl+C로 정리해라.")
            readlnOrNull()
        }
    }
}
println("서비스와 컨테이너·볼륨을 정리했다.")
