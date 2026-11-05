import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.Executors

class SortingLab(private val db: ClickHouse) {
    @Volatile private var prepared = false

    @Synchronized
    fun prepare(): String {
        check(!prepared) { "already prepared" }
        require(Tuning.order.sorted() == listOf("event_time", "event_type", "tenant"))
        require(Tuning.partition in setOf("toYYYYMM(event_time)", "toDate(event_time)"))
        db.sql("CREATE DATABASE sorting_lab")
        val keys = mapOf("a" to Tuning.order, "b" to listOf("event_time", "tenant", "event_type"),
            "c" to listOf("event_type", "tenant", "event_time"))
        for ((table, order) in keys) {
            val partition = if (table == "a") Tuning.partition else "toYYYYMM(event_time)"
            db.sql("""
                CREATE TABLE sorting_lab.$table (
                    id UInt64, tenant UInt16, event_time DateTime('UTC'), event_type UInt8, amount UInt64
                ) ENGINE = MergeTree PARTITION BY $partition ORDER BY (${order.joinToString(",")})
                SETTINGS index_granularity = 4096, index_granularity_bytes = 0
            """.trimIndent())
            db.sql("""
                INSERT INTO sorting_lab.$table
                SELECT number, number % 64,
                    toDateTime('2026-01-01 00:00:00', 'UTC') + toIntervalDay(intDiv(number,64) % 32)
                        + toIntervalSecond(intDiv(number,8192)),
                    intDiv(number,2048) % 4, number % 97 + 1
                FROM numbers(1048576) SETTINGS max_threads=1, max_insert_threads=1, max_block_size=2097152
            """.trimIndent())
            db.sql("OPTIMIZE TABLE sorting_lab.$table FINAL")
        }
        prepared = true
        return "ready"
    }

    fun query(params: Map<String, String>): QueryResult {
        check(prepared)
        val table = params["table"] ?: "a"
        require(table in setOf("a", "b", "c"))
        val tenant = (params["tenant"] ?: "17").toInt().also { require(it in 0..63) }
        val day = (params["day"] ?: "7").toInt().also { require(it in 0..31) }
        val type = (params["type"] ?: "2").toInt().also { require(it in 0..3) }
        val range = "event_time >= toDateTime('2026-01-01 00:00:00','UTC') + INTERVAL $day DAY " +
            "AND event_time < toDateTime('2026-01-01 00:00:00','UTC') + INTERVAL ${day + 1} DAY"
        val predicate = when (params["kind"] ?: "tenant") {
            "tenant" -> "tenant = $tenant"
            "day" -> range
            "type" -> "event_type = $type"
            "window" -> "tenant = $tenant AND $range"
            else -> throw IllegalArgumentException("unknown kind")
        }
        val id = UUID.randomUUID().toString()
        val sum = db.sql("""
            SELECT sum(amount) FROM sorting_lab.$table WHERE $predicate
            SETTINGS max_threads=1, use_query_cache=0, use_query_condition_cache=0,
                optimize_move_to_prewhere=0, log_queries=1, log_queries_probability=1 FORMAT TSV
        """.trimIndent(), id).toLong()
        return QueryResult(id, sum)
    }

    fun storage(): String = db.sql("""
        SELECT table, sum(rows), sum(data_compressed_bytes), sum(data_uncompressed_bytes), count()
        FROM system.parts WHERE database='sorting_lab' AND active GROUP BY table ORDER BY table FORMAT TSV
    """.trimIndent())

    fun observe(): String = buildString {
        for (table in listOf("a", "b", "c")) {
            for (kind in listOf("tenant", "day", "type")) {
                val result = query(mapOf("table" to table, "kind" to kind))
                val stats = db.stats(result.id)
                appendLine("$table\t$kind\t${result.sum}\t${stats.rows}\t${stats.marks}")
            }
        }
    }.trimEnd()
}

fun parameters(exchange: HttpExchange): Map<String, String> =
    exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
        val fields = it.split('=', limit = 2)
        URLDecoder.decode(fields[0], Charsets.UTF_8) to URLDecoder.decode(fields.getOrElse(1) { "" }, Charsets.UTF_8)
    }

fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = (body + "\n").toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

fun main() {
    val db = ClickHouse(System.getenv("CLICKHOUSE_URL") ?: error("CLICKHOUSE_URL required"))
    val lab = SortingLab(db)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", System.getenv("PORT").toInt()), 0)
    server.executor = Executors.newVirtualThreadPerTaskExecutor()
    server.createContext("/") { exchange ->
        exchange.use {
            try {
                val route = exchange.requestMethod + " " + exchange.requestURI.path
                val body = when (route) {
                    "GET /health" -> { db.sql("SELECT 1"); "ok" }
                    "POST /prepare" -> lab.prepare()
                    "GET /query" -> lab.query(parameters(exchange)).let { "${it.id}\t${it.sum}" }
                    "GET /observe" -> lab.observe()
                    "GET /storage" -> lab.storage()
                    else -> { respond(exchange, 404, "not found"); return@createContext }
                }
                respond(exchange, 200, body)
            } catch (_: IllegalArgumentException) {
                respond(exchange, 400, "invalid request")
            } catch (error: Exception) {
                System.err.println(error.message)
                respond(exchange, 500, "operation failed")
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(0) })
    server.start()
}
