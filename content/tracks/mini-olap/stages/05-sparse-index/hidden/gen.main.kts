import kotlin.random.Random

class Options(val rows: Int, val queries: Int, val seed: Long)

fun parseOptions(): Options {
    val values = mutableMapOf("--rows" to "240", "--queries" to "180", "--seed" to "1")
    require(args.size % 2 == 0) { "--rows N --queries N --seed N" }
    for ((name, value) in args.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    val options = Options(values.getValue("--rows").toInt(), values.getValue("--queries").toInt(), values.getValue("--seed").toLong())
    require(options.rows in 0..2_000_000 && options.queries in 0..100_000)
    return options
}

val options = parseOptions()
val random = Random(options.seed)
val output = System.out.bufferedWriter()
val commonWords = listOf("", "a", "b", "NULL", "가", "서울")
val rareWords = listOf("희귀키", " Z ", "a|b", "z~", "끝")

fun emit(line: String) {
    output.appendLine(line)
}

fun word(): String = if (random.nextInt(10) == 0) rareWords.random(random) else commonWords.random(random)

fun nullableInt(): String = if (random.nextInt(9) == 0) "NULL" else random.nextInt(-2, 9).toString()

fun insertRow() {
    emit("INSERT INTO events VALUES (${nullableInt()}, ${nullableInt()}, ${random.nextInt(-50, 51)}, '${word()}')")
}

fun copyRows(count: Int) {
    emit("COPY events FROM STDIN")
    repeat(count) {
        val tenant = nullableInt().replace("NULL", "\\N")
        val day = nullableInt().replace("NULL", "\\N")
        val amount = if (random.nextInt(6) == 0) "\\N" else random.nextInt(-50, 51).toString()
        val user = if (random.nextInt(7) == 0) "\\N" else word()
        emit("$tenant\t$day\t$amount\t$user")
    }
    emit("\\.")
}

fun emitFault(index: Int) {
    val commands = listOf(
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, a) GRANULARITY 0",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a) GRANULARITY 2",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, b, a) GRANULARITY 2",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, b) GRANULARITY 0",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, b) GRANULARITY 65537",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, b) GRANULARITY -1",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, missing) GRANULARITY 0",
        "CREATE TABLE daily (a INT, b INT) ORDER BY (missing, b) GRANULARITY 0",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, b)",
        "CREATE TABLE bad (a INT, b INT) ORDER BY (a, b) GRANULARITY 007",
        "SCAN SELECT COUNT(*) FROM plain",
        "SCAN SELECT missing FROM plain",
        "SCAN SELECT SUM(s) FROM plain",
        "SCAN SELECT COUNT(*) FROM nope",
        "SCAN EXPLAIN SELECT COUNT(*) FROM events",
        "SCAN ESTIMATE daily FROM 0 TO 1",
        "SCAN SELECT COUNT(*) FROM events ORDER BY SUM(amount)",
        "SCAN SELECT APPROX_COUNT_DISTINCT(*) FROM events",
        "SCAN SELECT COUNT(*) FROM events WHERE tenant = '1'",
        "SCAN SELECT user FROM events",
        "INSERT INTO events VALUES (1, 2, 3, 'rollback'), (1, 2)",
        "INSERT INTO events VALUES (1, 2, 3, 'rollback'), (1, 2, 3, 4)",
        "ESTIMATE daily FROM 3 TO 1",
        "CREATE SKETCH bad ON events (user) BY user PRECISION 3",
        "CREATE AGGREGATE bad AS SELECT tenant, APPROX_COUNT_DISTINCT(user) FROM events GROUP BY tenant",
        "SCAN SELECT COUNT(*) FROM events;",
    )
    emit(commands[index % commands.size])
    if (index % 8 == 0) {
        emit("COPY events FROM STDIN")
        emit("1\t2\t3\trollback")
        emit("x\t2\t3\tbroken")
        emit("\\.")
    }
}

fun scanQuery(index: Int) {
    val value = random.nextInt(-3, 12)
    val filters = listOf(
        "", " WHERE tenant = $value", " WHERE tenant >= $value AND tenant <= ${value + 2}",
        " WHERE tenant > $value AND tenant < ${value + 2}", " WHERE day = $value",
        " WHERE tenant = $value AND day = 2", " WHERE tenant <= $value AND tenant >= $value AND day > 1 AND day <= 3",
        " WHERE tenant = $value AND tenant = ${value + 1}", " WHERE tenant = 1 AND day > 2 AND day <= 2",
        " WHERE tenant IS NULL", " WHERE tenant IS NOT NULL AND day = 2", " WHERE tenant = NULL",
        " WHERE tenant <> $value", " WHERE tenant = $value OR day = 2", " WHERE NOT tenant < $value",
        " WHERE tenant = 1 AND (day >= 2 OR amount IS NULL)", " WHERE (tenant >= $value AND tenant <= ${value + 1}) AND amount > 0",
        " WHERE tenant > $value AND tenant < ${value + 1}", " WHERE tenant = 1 AND day >= 2 AND day < 2",
    )
    val filter = filters[index % filters.size]
    val query = when (index % 7) {
        0 -> "SELECT COUNT(*), SUM(amount), AVG(amount) FROM events$filter"
        1 -> "SELECT tenant, day, GROUPING(tenant), GROUPING(day), COUNT(*) FROM events$filter GROUP BY ROLLUP(tenant, day) ORDER BY COUNT(*) DESC LIMIT 6"
        2 -> "SELECT tenant, APPROX_COUNT_DISTINCT(user), COUNT(DISTINCT user), MIN(user) FROM events$filter GROUP BY CUBE(tenant) LIMIT 7"
        3 -> "SELECT day, COUNT(*), MAX(amount) FROM events$filter GROUP BY GROUPING SETS ((day), ()) ORDER BY day DESC NULLS LAST LIMIT 4"
        4 -> "SELECT COUNT(*) FROM events$filter LIMIT 0"
        5 -> "SELECT APPROX_COUNT_DISTINCT(day), APPROX_COUNT_DISTINCT(user) FROM events$filter"
        else -> "SELECT tenant, COUNT(*), SUM(amount), COUNT(amount) FROM events$filter GROUP BY tenant LIMIT 5"
    }
    emit("SCAN $query")
    if (index % 7 == 6) {
        emit("EXPLAIN $query")
        emit(query)
    }
}

fun smallSession() {
    val granularity = listOf(1, 2, 3, 7, 16, 65_536).random(random)
    emit("CREATE TABLE events (tenant INT, day INT, amount INT, user STRING) ORDER BY (tenant, day) GRANULARITY $granularity")
    emit("CREATE TABLE plain (s STRING)")
    emit("CREATE TABLE empty_t (a STRING, b INT) ORDER BY (a, b) GRANULARITY 2")
    emit("CREATE TABLE words (a STRING, b STRING, v INT) ORDER BY (a, b) GRANULARITY 2")
    emit("CREATE SKETCH daily ON events (user) BY day PRECISION 4")
    emit("CREATE AGGREGATE totals AS SELECT tenant, COUNT(*), SUM(amount), COUNT(amount) FROM events GROUP BY tenant")
    copyRows(options.rows)
    emit("INSERT INTO events VALUES (1, 2, 1, ''), (1, 2, 2, '가'), (1, 2, 3, NULL), (NULL, NULL, 0, 'NULL')")
    emit("INSERT INTO words VALUES ('가', '', 1), ('a', 'b', 2), ('a', 'b', 3), ('', 'z', 4), (NULL, 'b', 5), ('a', NULL, 6)")
    repeat(26) { emitFault(it) }
    for (filter in listOf("a = 'a' AND b = 'b'", "a >= '' AND a < '가'", "b = 'b'", "a IS NULL", "a = 'z~'", "a = 'a' AND b > 'b' AND b < 'b'")) {
        emit("SCAN SELECT a, b, COUNT(*), SUM(v) FROM words WHERE $filter GROUP BY ROLLUP(a, b)")
    }
    emit("SCAN SELECT COUNT(*), APPROX_COUNT_DISTINCT(a) FROM empty_t")
    emit("SCAN SELECT a, COUNT(*) FROM empty_t GROUP BY ROLLUP(a)")
    repeat(options.queries) { index ->
        if (index % 23 == 0) insertRow()
        if (index % 41 == 0) copyRows(2)
        if (index % 19 == 0) emitFault(random.nextInt(26))
        if (index % 31 == 0) emit("ESTIMATE daily FROM -3 TO 10")
        scanQuery(index)
    }
    emit("SCAN SELECT COUNT(*) FROM events WHERE tenant = 1 AND day = 2")
    insertRow()
    emit("SCAN SELECT COUNT(*) FROM events WHERE tenant = 1 AND day = 2")
}

fun gcd(left: Int, right: Int): Int {
    var remainder = left
    var divisor = right
    while (divisor != 0) {
        val next = remainder % divisor
        remainder = divisor
        divisor = next
    }
    return remainder
}

fun largeSession() {
    emit("CREATE TABLE events (tenant INT, day INT, amount INT) ORDER BY (tenant, day) GRANULARITY 128")
    emit("COPY events FROM STDIN")
    var stride = 7_919
    while (gcd(stride, options.rows) != 1) stride += 2
    val offset = random.nextInt(options.rows)
    repeat(options.rows) { index ->
        val key = ((index.toLong() * stride + offset) % options.rows).toInt()
        emit("${key / 10}\t${key % 10}\t${random.nextInt(-100, 101)}")
    }
    emit("\\.")
    val tenants = (options.rows + 9) / 10
    repeat(options.queries) { index ->
        if (index == options.queries / 3 || index == options.queries * 2 / 3) {
            emit("INSERT INTO events VALUES (1, 2, 7), (1, 2, 8)")
        }
        val tenant = random.nextInt(tenants + 5)
        val filter = when {
            index < 5 -> "day = ${random.nextInt(10)}"
            index % 13 == 0 -> "tenant > $tenant AND tenant < ${tenant + 2}"
            index % 11 == 0 -> "tenant = $tenant AND day >= 2 AND day <= 5"
            index % 7 == 0 -> "tenant >= $tenant AND tenant <= ${tenant + 2}"
            else -> "tenant = $tenant"
        }
        emit("SCAN SELECT COUNT(*) FROM events WHERE $filter")
    }
}

if (options.rows >= 100_000) largeSession() else smallSession()
output.flush()
