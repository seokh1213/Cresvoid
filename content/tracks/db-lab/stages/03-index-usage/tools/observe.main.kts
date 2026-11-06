@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration

fun sql(stack: ComposeStack, query: String): String = stack.exec(
    "mysql", "mysql", "-uroot", "--batch", "-e", query,
)

fun seed(stack: ComposeStack) {
    val output = sql(stack, File("catalog.sql").readText())
    check(!output.startsWith("ERROR")) { output.take(300) }
    check(sql(stack, "SELECT COUNT(*) FROM lab.products").trim().endsWith("\n1000000"))
}

fun startApp(stack: ComposeStack, program: File) {
    val process = ProcessBuilder(
        "docker", "compose", "-p", stack.project, "-f", File("compose.yaml").absolutePath,
        "up", "-d", "--wait", "--wait-timeout", "120", "--scale", "app=1", "app",
    ).redirectErrorStream(true).apply {
        environment().putAll(LAB_IMAGES + programEnvironment(program))
    }.start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "App startup: ${output.takeLast(1500)}" }
}

fun events(stack: ComposeStack, token: String) {
    require(token.matches(Regex("r[0-9]+"))) { "token은 r 뒤에 숫자를 붙여라." }
    println(sql(stack, """
        SELECT SQL_TEXT, ROWS_EXAMINED, ROWS_SENT, MYSQL_ERRNO, MESSAGE_TEXT
        FROM performance_schema.events_statements_history_long
        WHERE SQL_TEXT LIKE '/*lab03:$token*/%'
    """.trimIndent()))
}

fun observe(stack: ComposeStack) {
    val base = "http://127.0.0.1:${stack.port("app", 8080)}"
    println("READY project=${stack.project} url=$base")
    println("sql <한 줄 SQL> | get <인코딩한 쿼리 문자열> | events <token> | quit")
    HttpClient.newHttpClient().use { client ->
        val input = System.`in`.bufferedReader()
        while (true) {
            val line = input.readLine() ?: break
            if (line == "quit") break
            try {
                when {
                    line.startsWith("sql ") -> println(sql(stack, "USE lab; ${line.removePrefix("sql ")}"))
                    line.startsWith("events ") -> events(stack, line.removePrefix("events "))
                    line.startsWith("get ") -> {
                        val query = line.removePrefix("get ")
                        val response = client.send(
                            get(base, "/query?$query", Duration.ofSeconds(60)), HttpResponse.BodyHandlers.ofString(),
                        )
                        println("GET $query\nHTTP ${response.statusCode()} body=[${response.body()}]")
                    }
                    else -> println("sql / get / events / quit 중 하나를 입력해라.")
                }
            } catch (error: Exception) {
                System.err.println("관찰 실패: ${error.message}")
            }
        }
    }
}

require(args.size == 1) { "프로그램 디렉터리의 절대 경로를 넘겨라." }
val program = File(args[0]).canonicalFile
require(File(program, "build/app.jar").isFile) { "먼저 build.sh를 실행해라." }
requireDocker()
ComposeStack(File("compose.yaml"), "db03-observe", programEnvironment(program)).use { stack ->
    stack.up(180)
    seed(stack)
    startApp(stack, program)
    observe(stack)
}
