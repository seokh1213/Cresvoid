@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun query(client: HttpClient, base: String, sql: String): HttpResponse<String> {
    val request = HttpRequest.newBuilder(URI.create(base))
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.ofString(sql))
        .build()
    return client.send(request, HttpResponse.BodyHandlers.ofString())
}

fun prepare(client: HttpClient, base: String) {
    val statements = listOf(
        "CREATE DATABASE lab",
        """CREATE TABLE lab.events (
            tenant UInt32, id UInt32, version UInt64, state String, amount Int64
        ) ENGINE = ReplacingMergeTree(version) ORDER BY (tenant, id)""",
        "SYSTEM STOP MERGES lab.events",
    ) + listOf(
        "(1,1,1,'active',100)", "(1,2,1,'hold',40)",
        "(1,1,3,'hold',70)", "(1,2,3,'active',25)",
        "(1,1,2,'active',150)", "(1,2,2,'hold',90)",
    ).map { "INSERT INTO lab.events VALUES $it" }
    for (statement in statements) {
        val response = query(client, base, statement)
        check(response.statusCode() == 200) { "Fixture HTTP ${response.statusCode()}: ${response.body()}" }
    }
}

require(args.size == 1) { "Usage: kotlin diagnose.main.kts <generated-sql-file>" }
val sql = File(args.single()).readText()
requireDocker()
val compose = File("content/tracks/olap-lab/stages/02-replacing-merge/hidden/compose.yaml")
ComposeStack(compose, "olap-replacing-diagnosis").use { stack ->
    stack.up()
    val base = "http://127.0.0.1:${stack.port("clickhouse", 8123)}"
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
        prepare(client, base)
        val response = query(client, base, sql)
        println("HTTP ${response.statusCode()}")
        print(response.body())
    }
}
