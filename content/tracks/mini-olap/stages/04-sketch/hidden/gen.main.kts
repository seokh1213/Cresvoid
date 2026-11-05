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
    require(options.rows in 0..1_000_000 && options.queries in 0..5_000)
    return options
}

val options = parseOptions()
val random = Random(options.seed)
val output = System.out.bufferedWriter()
val commonUsers = listOf("a", "b", "user", "", "NULL", "가", "서울")
val rareUsers = listOf("희귀방문자", " Z ", "a|b", "z~", "방문자끝")

fun emit(line: String) {
    output.appendLine(line)
}

fun user(): String = when (random.nextInt(10)) {
    0 -> rareUsers.random(random)
    1, 2, 3, 4, 5 -> commonUsers.random(random)
    else -> "user${random.nextInt(128)}"
}

fun copyRows(count: Int) {
    emit("COPY visits FROM STDIN")
    repeat(count) {
        val day = if (random.nextInt(12) == 0) "\\N" else random.nextInt(-3, 13).toString()
        val value = if (random.nextInt(9) == 0) "\\N" else user()
        val amount = if (random.nextInt(5) == 0) "\\N" else random.nextInt(-100, 101).toString()
        emit("$day\t$value\t$amount")
    }
    emit("\\.")
}

fun insertRow() {
    val day = if (random.nextInt(10) == 0) "NULL" else random.nextInt(-3, 13).toString()
    val value = if (random.nextInt(8) == 0) "NULL" else "'${user()}'"
    emit("INSERT INTO visits VALUES ($day, $value, ${random.nextInt(-100, 101)})")
}

fun emitFault(index: Int) {
    val commands = listOf(
        "CREATE SKETCH p4 ON nope (missing) BY day PRECISION 2",
        "CREATE SKETCH bad ON nope (missing) BY day PRECISION 4",
        "CREATE SKETCH bad ON visits (missing) BY missing_day PRECISION 4",
        "CREATE SKETCH bad ON visits (user) BY missing_day PRECISION 4",
        "CREATE SKETCH bad ON visits (user) BY user PRECISION 3",
        "CREATE SKETCH bad ON visits (user) BY day PRECISION 3",
        "CREATE SKETCH bad ON visits (user) BY day PRECISION 13",
        "CREATE SKETCH bad ON visits (user) BY day PRECISION 007",
        "CREATE SKETCH bad ON visits (*) BY day PRECISION 4",
        "ESTIMATE visits FROM 1 TO 2",
        "ESTIMATE nowhere FROM 3 TO 1",
        "ESTIMATE p4 FROM 3 TO 1",
        "ESTIMATE p4 FROM '1' TO 2",
        "CREATE TABLE p4 (x INT)",
        "CREATE AGGREGATE p4 AS SELECT day, COUNT(*) FROM visits GROUP BY day",
        "SELECT COUNT(*) FROM p4",
        "INSERT INTO p4 VALUES (1)",
        "CREATE SKETCH bad ON p4 (user) BY day PRECISION 4",
        "CREATE AGGREGATE bad AS SELECT day, APPROX_COUNT_DISTINCT(user) FROM visits GROUP BY day",
        "SELECT APPROX_COUNT_DISTINCT(*) FROM visits",
        "SELECT APPROX_COUNT_DISTINCT(DISTINCT user) FROM visits",
        "SELECT APPROX_COUNT_DISTINCT(user, 4) FROM visits",
        "SELECT APPROX_COUNT_DISTINCT(missing), SUM(user) FROM visits",
        "SELECT APPROX_COUNT_DISTINCT(user) FROM visits ORDER BY COUNT(*)",
        "SELECT user, APPROX_COUNT_DISTINCT(user) FROM visits",
        "INSERT INTO visits VALUES (1, 'rollback', 0), (2, 'broken')",
        "INSERT INTO visits VALUES (1, 'rollback', 0), (2, 7, 0)",
        "SELECT COUNT(*) FROM visits;",
        "select COUNT(*) FROM visits",
    )
    emit(commands[index % commands.size])
    if (index % 7 == 0) {
        emit("COPY visits FROM STDIN")
        emit("1\trollback\t1")
        emit("2\tbroken\tx")
        emit("\\.")
        emit("COPY p4 FROM STDIN")
        emit("ignored")
        emit("\\.")
    }
}

fun emitQuery(index: Int) {
    val from = random.nextInt(-5, 16)
    val to = from + random.nextInt(9)
    when (index % 12) {
        0 -> emit("ESTIMATE ${listOf("p4", "p5", "p6", "p12").random(random)} FROM $from TO $to")
        1 -> emit("SELECT COUNT(DISTINCT user), APPROX_COUNT_DISTINCT(user), APPROX_COUNT_DISTINCT(user), SUM(amount), AVG(amount) FROM visits")
        2 -> emit("SELECT day, GROUPING(day), APPROX_COUNT_DISTINCT(user), COUNT(*) FROM visits GROUP BY ROLLUP(day) ORDER BY APPROX_COUNT_DISTINCT(user) DESC")
        3 -> emit("SELECT APPROX_COUNT_DISTINCT(day), MIN(user), MAX(amount) FROM visits WHERE NOT (day < $from OR user IS NULL)")
        4 -> emit("SELECT day, APPROX_COUNT_DISTINCT(user) FROM visits WHERE amount >= 0 GROUP BY CUBE(day) LIMIT 5")
        5 -> emit("SELECT day, APPROX_COUNT_DISTINCT(user), COUNT(user) FROM visits GROUP BY GROUPING SETS ((day), ()) ORDER BY APPROX_COUNT_DISTINCT(user), day DESC NULLS LAST LIMIT 8")
        6 -> emit("EXPLAIN SELECT APPROX_COUNT_DISTINCT(user) FROM visits")
        7 -> emit("EXPLAIN SELECT day, SUM(amount), AVG(amount) FROM visits GROUP BY day")
        8 -> emit("SELECT APPROX_COUNT_DISTINCT(user) FROM visits WHERE day = NULL")
        9 -> emit("SELECT APPROX_COUNT_DISTINCT(x), COUNT(*) FROM empty_t")
        10 -> emit("ESTIMATE empty_s FROM $from TO $to")
        else -> emit("SELECT day, COUNT(*), SUM(amount), MIN(user), MAX(user) FROM visits GROUP BY day ORDER BY COUNT(*) DESC LIMIT 3")
    }
}

fun smallSession() {
    emit("CREATE TABLE visits (day INT, user STRING, amount INT)")
    emit("CREATE TABLE empty_t (day INT, x INT)")
    emit("CREATE SKETCH empty_s ON empty_t (x) BY day PRECISION 10")
    emit("CREATE SKETCH p4 ON visits (user) BY day PRECISION 4")
    emit("CREATE AGGREGATE totals AS SELECT day, COUNT(*), SUM(amount), COUNT(amount) FROM visits GROUP BY day")
    copyRows(options.rows / 2)
    emit("CREATE SKETCH p5 ON visits (user) BY day PRECISION 5")
    copyRows(options.rows - options.rows / 2)
    emit("CREATE SKETCH p6 ON visits (user) BY day PRECISION 6")
    emit("CREATE SKETCH p12 ON visits (amount) BY day PRECISION 12")
    emit("INSERT INTO visits VALUES (3, NULL, NULL), (NULL, '날짜없음', 0)")
    repeat(29) { emitFault(it) }
    repeat(options.queries) { index ->
        if (index % 17 == 0) insertRow()
        if (index % 37 == 0) copyRows(3)
        if (index % 19 == 0) emitFault(random.nextInt(29))
        emitQuery(index)
    }
    emit("ESTIMATE p4 FROM -3 TO 12")
    emit("ESTIMATE p4 FROM 0 TO 0")
    emit("ESTIMATE p4 FROM -3 TO 12")
}

fun largeSession() {
    emit("CREATE TABLE visits (day INT, user STRING)")
    val create = "CREATE SKETCH daily ON visits (user) BY day PRECISION 6"
    val beforeCopy = random.nextBoolean()
    if (beforeCopy) emit(create)
    emit("COPY visits FROM STDIN")
    repeat(options.rows) { index ->
        val day = (index + random.nextInt(64)) % 64
        val value = when (val code = random.nextInt(131_072)) {
            0 -> "\\N"
            1 -> ""
            2 -> "가"
            else -> "u$code"
        }
        emit("$day\t$value")
    }
    emit("\\.")
    if (!beforeCopy) emit(create)
    repeat(options.queries) { index ->
        if (index == options.queries / 3) emit("INSERT INTO visits VALUES (1, 'new'), (2, 'new'), (NULL, 'ignored')")
        if (index == options.queries / 2) {
            emit("COPY visits FROM STDIN")
            emit("3\tnew")
            emit("\\.")
        }
        val from = random.nextInt(0, 17)
        emit("ESTIMATE daily FROM $from TO ${from + random.nextInt(32, 49) - 1}")
    }
}

if (options.rows >= 100_000) largeSession() else smallSession()
output.flush()
