import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.random.Random

class Options(val rows: Int, val selects: Int, val distinct: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--rows" to "90", "--selects" to "90", "--distinct" to "8", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        rows = values.getValue("--rows").toInt(),
        selects = values.getValue("--selects").toInt(),
        distinct = values.getValue("--distinct").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.rows >= 100_000
val output = BufferedWriter(OutputStreamWriter(System.out, Charsets.UTF_8), 1 shl 16)

fun emit(line: String) {
    output.write(line)
    output.write("\n")
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun skewedIndex(size: Int): Int = (size * random.nextDouble() * random.nextDouble()).toInt().coerceIn(0, size - 1)

fun <T> pickDistinct(items: List<T>, count: Int): List<T> = items.shuffled(random).take(count)

enum class Type { INT, STRING }

sealed interface Pool {
    fun draw(): Any?
}

class TextPool(private val common: List<String>, private val rare: List<String>, private val nullRate: Double) : Pool {
    override fun draw(): Any? = when {
        chance(nullRate) -> null
        rare.isNotEmpty() && chance(0.12) -> pick(rare)
        else -> common[skewedIndex(common.size)]
    }
}

class NumberPool(private val range: IntRange, private val extremes: List<Int>, private val nullRate: Double) : Pool {
    override fun draw(): Any? = when {
        chance(nullRate) -> null
        extremes.isNotEmpty() && chance(0.08) -> pick(extremes)
        else -> random.nextInt(range.first, range.last + 1)
    }
}

class ColumnModel(val name: String, val type: Type, val pool: Pool)

class TableModel(val name: String, val columns: List<ColumnModel>) {
    val numeric: List<ColumnModel> get() = columns.filter { it.type == Type.INT }
}

val INT_EXTREMES = listOf(1_000_000_000, -1_000_000_000, 0, 1, -1, 999_999_999)
val ODD_TEXTS = listOf("", "NULL", "null", " ", "a b", "x|y", "East", "east ", "Z", "가", "가나", "힣", "~", "0", "-1", "B", "a")
val SYMBOL_TOKENS = setOf("(", ")", ",", "*", "=", "<>", "<", "<=", ">", ">=")
val OPERATORS = listOf("=", "<>", "<", "<=", ">", ">=")
val UNKNOWN_TABLES = listOf("nope", "sale", "visit", "event")
val UNKNOWN_COLUMNS = listOf("city", "zip", "qty2", "regionx", "total")

fun literal(value: Any?): String = when (value) {
    null -> "NULL"
    is Int -> value.toString()
    is String -> "'$value'"
    else -> error("값이 아니다: $value")
}

fun cellText(value: Any?): String = when (value) {
    null -> "\\N"
    else -> value.toString()
}

fun command(tokens: List<String>): String {
    val text = StringBuilder()
    for ((index, token) in tokens.withIndex()) {
        if (index > 0) {
            val touchesSymbol = token in SYMBOL_TOKENS || tokens[index - 1] in SYMBOL_TOKENS
            val spaces = if (touchesSymbol && chance(0.3)) 0 else if (chance(0.06)) 2 else 1
            repeat(spaces) { text.append(' ') }
        }
        text.append(token)
    }
    return (if (chance(0.04)) "  " else "") + text + (if (chance(0.04)) " " else "")
}

fun listTokens(elements: List<List<String>>): List<String> =
    elements.flatMapIndexed { index, element -> (if (index > 0) listOf(",") else emptyList()) + element }

fun createTableTokens(table: TableModel): List<String> =
    listOf("CREATE", "TABLE", table.name, "(") + listTokens(table.columns.map { listOf(it.name, it.type.name) }) + listOf(")")

fun rowTokens(table: TableModel): List<String> =
    listOf("(") + listTokens(table.columns.map { listOf(literal(it.pool.draw())) }) + listOf(")")

fun insertTokens(table: TableModel, rows: List<List<String>>): List<String> =
    listOf("INSERT", "INTO", table.name, "VALUES") + listTokens(rows)

class SelectShape(
    val items: List<List<String>>,
    val table: String,
    val where: List<String>?,
    val groupBy: List<String>,
    val orderBy: List<List<String>>,
    val limit: String?,
) {
    fun tokens(): List<String> {
        val tokens = mutableListOf("SELECT")
        tokens += listTokens(items)
        tokens += listOf("FROM", table)
        if (where != null) tokens += listOf("WHERE") + where
        if (groupBy.isNotEmpty()) tokens += listOf("GROUP", "BY") + listTokens(groupBy.map { listOf(it) })
        if (orderBy.isNotEmpty()) tokens += listOf("ORDER", "BY") + listTokens(orderBy)
        if (limit != null) tokens += listOf("LIMIT", limit)
        return tokens
    }
}

fun orderKey(item: List<String>): List<String> {
    val direction = when (random.nextInt(3)) {
        0 -> listOf("ASC")
        1 -> listOf("DESC")
        else -> emptyList()
    }
    val nulls = when (random.nextInt(4)) {
        0 -> listOf("NULLS", "FIRST")
        1 -> listOf("NULLS", "LAST")
        else -> emptyList()
    }
    return item + direction + nulls
}

fun comparison(table: TableModel): List<String> {
    val column = pick(table.columns)
    return when {
        chance(0.12) -> listOf(column.name, "IS", "NULL")
        chance(0.12) -> listOf(column.name, "IS", "NOT", "NULL")
        else -> listOf(column.name, pick(OPERATORS), literal(if (chance(0.07)) null else column.pool.draw() ?: column.pool.draw()))
    }
}

fun condition(table: TableModel, depth: Int): List<String> = when {
    depth < 2 && chance(0.32) -> condition(table, depth + 1) + pick(listOf("AND", "OR")) + condition(table, depth + 1)
    depth < 3 && chance(0.14) -> listOf("NOT") + condition(table, depth + 1)
    depth < 3 && chance(0.14) -> listOf("(") + condition(table, depth + 1) + listOf(")")
    else -> comparison(table)
}

fun aggregateItem(table: TableModel): List<String> {
    val column = pick(table.columns)
    val numeric = table.numeric.ifEmpty { return listOf("COUNT", "(", "DISTINCT", column.name, ")") }
    return when (random.nextInt(11)) {
        0, 1 -> listOf("COUNT", "(", "*", ")")
        2 -> listOf("COUNT", "(", column.name, ")")
        3, 4 -> listOf("COUNT", "(", "DISTINCT", column.name, ")")
        5, 10 -> listOf("SUM", "(", pick(numeric).name, ")")
        6, 7 -> listOf("AVG", "(", pick(numeric).name, ")")
        8 -> listOf("MIN", "(", column.name, ")")
        else -> listOf("MAX", "(", column.name, ")")
    }
}

fun randomSelect(table: TableModel, groupable: List<ColumnModel>): SelectShape {
    val groupColumns = if (chance(0.6)) pickDistinct(groupable, 1 + skewedIndex(3)) else emptyList()
    val items = mutableListOf<List<String>>()
    groupColumns.filter { chance(0.8) }.forEach { items += listOf(it.name) }
    repeat(random.nextInt(if (items.isEmpty()) 1 else 0, 4)) { items += aggregateItem(table) }
    if (chance(0.06) && items.isNotEmpty()) items += pick(items)
    val shuffled = if (chance(0.3)) items.shuffled(random) else items
    val where = if (chance(0.5)) condition(table, 0) else null
    val orderBy = if (chance(0.5)) pickDistinct(shuffled, 1 + skewedIndex(3)).map(::orderKey) else emptyList()
    val limit = if (chance(0.3)) random.nextInt(0, 6).toString() else null
    return SelectShape(shuffled, table.name, where, groupColumns.map { it.name }, orderBy, limit)
}

val salesTable = TableModel(
    "sales",
    listOf(
        ColumnModel("region", Type.STRING, TextPool(listOf("east", "west", "north", "south", "서울", ""), ODD_TEXTS, 0.12)),
        ColumnModel("item", Type.STRING, TextPool(listOf("pen", "ink", "pencil", "pe", "Pen", "paper", "잉크", "pen2").take(options.distinct.coerceIn(2, 8)), ODD_TEXTS.take(4), 0.08)),
        ColumnModel("qty", Type.INT, NumberPool(-3..12, listOf(0, -1, 100), 0.15)),
        ColumnModel("price", Type.INT, NumberPool(-50..400, INT_EXTREMES, 0.12)),
    ),
)

val visitsTable = TableModel(
    "visits",
    listOf(
        ColumnModel("user", Type.STRING, TextPool((1..6).map { "u$it" }, listOf("U1", "u1 ", " u1", "", "NULL", "u1 x"), 0.1)),
        ColumnModel("page", Type.STRING, TextPool(listOf("/home", "/cart", "/a", "/b", "/검색", "/home/x"), listOf("", "/", "NULL"), 0.1)),
        ColumnModel("ms", Type.INT, NumberPool(0..900, listOf(-5, 1_000_000_000, 0), 0.2)),
        ColumnModel("count", Type.INT, NumberPool(-2..3, emptyList(), 0.3)),
    ),
)

val notesTable = TableModel(
    "notes",
    listOf(ColumnModel("body", Type.STRING, TextPool(listOf("", " ", "a", "NULL", "가"), emptyList(), 0.25))),
)

val smallTables = listOf(salesTable, visitsTable)

fun groupableColumns(table: TableModel): List<ColumnModel> = table.columns.filter { it.name != "price" && it.name != "ms" }

fun dataLine(table: TableModel): String = table.columns.joinToString("\t") { cellText(it.pool.draw()) }

fun badCell(column: ColumnModel): String = when (column.type) {
    Type.INT -> pick(listOf("007", "-0", "1000000001", " 7", "7 ", "", "NULL", "1e3", "+5", "\\n", "\\\\N", "abc", "12345678901234567890"))
    Type.STRING -> pick(listOf("it's", "a\\b", "\\\\N", "é", "\\n", "x'y", "\\"))
}

fun faultyDataLine(table: TableModel): String {
    val cells = table.columns.map { cellText(it.pool.draw()) }.toMutableList()
    return when (random.nextInt(9)) {
        0 -> cells.dropLast(1).joinToString("\t")
        1 -> (cells + cellText(table.columns.last().pool.draw())).joinToString("\t")
        2 -> cells.joinToString("\t") + "\t"
        3 -> ""
        4 -> pick(listOf(" \\.", "\\. ", "SELECT COUNT(*) FROM ${table.name}", "\\.\\.", "."))
        else -> {
            val broken = random.nextInt(table.columns.size)
            cells[broken] = badCell(table.columns[broken])
            cells.joinToString("\t")
        }
    }
}

fun emitCopy(table: TableModel, lines: Int, faultRate: Double) {
    emit(command(listOf("COPY", table.name, "FROM", "STDIN")))
    repeat(lines) { emit(if (chance(faultRate)) faultyDataLine(table) else dataLine(table)) }
    emit("\\.")
}

fun emitRejectedCopy(table: TableModel) {
    emit(
        pick(
            listOf(
                "COPY ${table.name} FROM stdin",
                "COPY ${table.name} STDIN",
                "COPY ${table.name} FROM STDIN extra",
                "COPY ${table.name.replaceFirstChar(Char::uppercase)} FROM STDIN",
                "COPY ${table.name} FROM STDIN;",
                "COPY",
                "COPY ${table.name}",
                "COPY ${pick(UNKNOWN_TABLES)} FROM STDIN",
                "COPY FROM STDIN",
                "COPY ${table.name}  FROM   STDIN\t",
            ),
        ),
    )
    repeat(random.nextInt(0, 3)) { emit(if (chance(0.5)) dataLine(table) else faultyDataLine(table)) }
    emit("\\.")
}

fun wrongType(column: ColumnModel): String =
    if (column.type == Type.INT) literal(pick(listOf("1", "east", ""))) else literal(random.nextInt(-5, 50))

fun faultyInsert() {
    val table = pick(smallTables)
    val good = { rowTokens(table) }
    val shortRow = listOf("(") + listTokens(table.columns.drop(1).map { listOf(literal(it.pool.draw())) }) + listOf(")")
    val typedRow = run {
        val broken = random.nextInt(table.columns.size)
        listOf("(") + listTokens(table.columns.mapIndexed { index, column -> listOf(if (index == broken) wrongType(column) else literal(column.pool.draw())) }) + listOf(")")
    }
    val line = when (random.nextInt(6)) {
        0 -> command(listOf("INSERT", "INTO", pick(UNKNOWN_TABLES), "VALUES") + good())
        1 -> command(insertTokens(table, listOf(good(), shortRow)))
        2 -> command(insertTokens(table, listOf(typedRow, shortRow)))
        3 -> command(insertTokens(table, listOf(good(), typedRow)))
        4 -> command(insertTokens(table, listOf(good()))) + ";"
        else -> "INSERT INTO ${table.name} VALUES ('a', 007)"
    }
    emit(line)
}

enum class Fault { NO_TABLE, NO_COLUMN, TYPE, NOT_GROUPED, NOT_SELECTED }

fun faultySelect(table: TableModel): String {
    val base = randomSelect(table, groupableColumns(table))
    val faults = Fault.entries.filter { chance(0.3) }.toSet().ifEmpty { setOf(pick(Fault.entries)) }
    var items = base.items
    var where = base.where
    var groupBy = base.groupBy
    var orderBy = base.orderBy
    var tableName = base.table
    if (Fault.NO_TABLE in faults) tableName = pick(UNKNOWN_TABLES)
    if (Fault.NO_COLUMN in faults) {
        val unknown = pick(UNKNOWN_COLUMNS)
        when (random.nextInt(4)) {
            0 -> items = items + listOf(listOf("COUNT", "(", "DISTINCT", unknown, ")"))
            1 -> where = (where?.plus("AND") ?: emptyList()) + listOf(unknown, "IS", "NULL")
            2 -> groupBy = groupBy + unknown
            else -> orderBy = orderBy + listOf(listOf(unknown))
        }
    }
    if (Fault.TYPE in faults) {
        val text = table.columns.first { it.type == Type.STRING }
        when (random.nextInt(3)) {
            0 -> items = items + listOf(listOf(pick(listOf("SUM", "AVG")), "(", text.name, ")"))
            1 -> where = listOf(pick(table.numeric).name, pick(OPERATORS), "'1'")
            else -> where = listOf(text.name, pick(OPERATORS), "1")
        }
    }
    if (Fault.NOT_GROUPED in faults) {
        val outside = table.columns.filter { it.name !in groupBy }
        if (outside.isNotEmpty()) items = items + listOf(listOf(pick(outside).name))
    }
    if (Fault.NOT_SELECTED in faults) {
        val column = table.columns.first().name
        val candidates = listOf(listOf("COUNT", "(", column, ")"), listOf("COUNT", "(", "DISTINCT", column, ")")).filter { it !in items }
        if (candidates.isNotEmpty()) orderBy = orderBy + listOf(pick(candidates))
    }
    return command(SelectShape(items, tableName, where, groupBy, orderBy, base.limit).tokens())
}

fun syntaxBrokenSelect(table: TableModel): String {
    val column = pick(table.columns)
    val head = listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name)
    return when (random.nextInt(10)) {
        0 -> command(listOf("SELECT", "COUNT", "(", "DISTINCT", "*", ")", "FROM", table.name))
        1 -> command(listOf("SELECT", "COUNT", "(", "DISTINCT", ")", "FROM", table.name))
        2 -> command(listOf("SELECT", "SUM", "(", "DISTINCT", column.name, ")", "FROM", table.name))
        3 -> command(listOf("SELECT", "COUNT", "(", "DISTINCT", column.name, ",", column.name, ")", "FROM", table.name))
        4 -> command(head + listOf("WHERE", "COUNT", "(", "DISTINCT", column.name, ")", ">", "1"))
        5 -> command(listOf("SELECT", "COUNT", "(", "distinct", "DISTINCT", column.name, ")", "FROM", table.name))
        6 -> command(head + listOf("WHERE", column.name, "!=", literal(column.pool.draw())))
        7 -> command(head + listOf("GROUP", "BY", column.name, ",", column.name))
        8 -> command(head + listOf("LIMIT", "-1"))
        else -> command(head) + ";"
    }
}

fun unknownCommand(): String = pick(
    listOf(
        "copy visits FROM STDIN",
        "Copy visits FROM STDIN",
        "COPY; visits FROM STDIN",
        "COPY2 visits FROM STDIN",
        "select COUNT(*) FROM sales",
        "SELECT\tCOUNT(*) FROM visits",
        "INSERT-INTO sales VALUES (1)",
    ),
)

fun emitSmallSession() {
    emit(command(createTableTokens(salesTable)))
    emit(command(createTableTokens(visitsTable)))
    if (chance(0.5)) emitCopy(visitsTable, 0, 0.0)
    emit(command(createTableTokens(notesTable)))
    var loaded = 0
    var selects = 0
    while (selects < options.selects || loaded < options.rows) {
        if (chance(0.03)) emit(if (chance(0.5)) "" else "  ")
        val roll = random.nextInt(100)
        when {
            loaded < options.rows && roll < 14 -> {
                val lines = 1 + random.nextInt(8)
                emitCopy(pick(smallTables), lines, 0.0)
                loaded += lines
            }
            loaded < options.rows && roll < 22 -> {
                val count = 1 + random.nextInt(4)
                val table = if (chance(0.6)) salesTable else visitsTable
                emit(command(insertTokens(table, List(count) { rowTokens(table) })))
                loaded += count
            }
            roll < 30 -> emitCopy(pick(smallTables), 1 + random.nextInt(5), 0.45)
            roll < 33 -> emitCopy(notesTable, random.nextInt(5), 0.2)
            roll < 36 -> emitRejectedCopy(pick(smallTables))
            roll < 40 -> faultyInsert()
            roll < 43 -> emit(unknownCommand())
            roll < 50 -> {
                emit(syntaxBrokenSelect(pick(smallTables)))
                selects++
            }
            roll < 60 -> {
                emit(faultySelect(pick(smallTables)))
                selects++
            }
            roll < 64 -> {
                emit(command(randomSelect(notesTable, notesTable.columns).tokens()))
                selects++
            }
            else -> {
                val table = pick(smallTables)
                emit(command(randomSelect(table, groupableColumns(table)).tokens()))
                selects++
            }
        }
    }
}

val BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

fun code(number: Int, length: Int, alphabet: String): String {
    val text = CharArray(length)
    var rest = number
    for (position in length - 1 downTo 0) {
        text[position] = alphabet[rest % alphabet.length]
        rest /= alphabet.length
    }
    return String(text)
}

fun emitLargeSession() {
    val userCount = options.distinct
    val users = List(userCount) { code(((it * 7_919L + 104_729L) % 14_776_336L).toInt(), 4, BASE62) }
    val pages = List(5_000) { if (it < 12) "/검색$it" else "/" + code(it * 37 % 46_656, 3, "0123456789abcdefghijklmnopqrstuvwxyz") }
    val countries = listOf("kr", "us", "jp", "de", "fr", "br", "in", "cn", "gb", "ca", "au", "mx", "es", "it", "nl", "se", "vn", "th", "", "NULL")
    val pickUser = { users[if (chance(0.5)) random.nextInt(userCount) else skewedIndex(userCount)] }
    emit("CREATE TABLE events (user STRING, page STRING, country STRING, day INT, items INT, ms INT)")
    emit("COPY events FROM STDIN")
    val line = StringBuilder()
    repeat(options.rows) {
        line.setLength(0)
        line.append(if (chance(0.01)) "\\N" else pickUser()).append('\t')
        line.append(if (chance(0.03)) "\\N" else pages[skewedIndex(pages.size)]).append('\t')
        line.append(if (chance(0.02)) "\\N" else countries[skewedIndex(countries.size)]).append('\t')
        line.append(if (chance(0.01)) "\\N" else (1 + random.nextInt(365)).toString()).append('\t')
        line.append(if (chance(0.04)) "\\N" else (1 + skewedIndex(20)).toString()).append('\t')
        line.append(if (chance(0.05)) "\\N" else (100 + random.nextInt(9_900)).toString())
        emit(line.toString())
    }
    emit("\\.")
    val pickPage = { pages[skewedIndex(pages.size)] }
    val pickCountry = { countries[skewedIndex(countries.size)] }
    val kinds = ScaleQuery.entries.flatMap { kind -> List(options.selects * kind.percent / 100) { kind } }
    val plan = (kinds + List(options.selects - kinds.size) { ScaleQuery.ONE_USER }).shuffled(random)
    for (kind in plan) {
        val query = when (kind) {
            ScaleQuery.TOP_USERS -> topUsersQuery(pickCountry)
            ScaleQuery.ONE_USER -> oneUserQuery(pickUser())
            ScaleQuery.DISTINCT -> distinctQuery(pickCountry)
            ScaleQuery.RANGE -> rangeQuery(pickPage, pickUser)
            ScaleQuery.MISC -> miscQuery(pickCountry)
        }
        emit(query)
    }
}

enum class ScaleQuery(val percent: Int) {
    TOP_USERS(40),
    ONE_USER(40),
    DISTINCT(14),
    RANGE(3),
    MISC(3),
}

fun topUsersQuery(pickCountry: () -> String): String {
    val measure = pick(listOf("COUNT(*)", "SUM(ms)", "AVG(ms)", "MAX(day)", "COUNT(items)", "SUM(items)", "MIN(page)"))
    val extra = pick(listOf("COUNT(*)", "AVG(items)", "MIN(day)", "MAX(page)"))
    val filter = when (random.nextInt(10)) {
        in 0 until 2 -> " WHERE country = '${pickCountry()}'"
        in 2 until 3 -> " WHERE day >= ${1 + random.nextInt(300)} AND day < ${100 + random.nextInt(266)}"
        else -> ""
    }
    val direction = pick(listOf("DESC", "DESC", "ASC", "DESC NULLS LAST", "ASC NULLS FIRST"))
    val tieBreak = if (chance(0.5)) ", user DESC" else ""
    val order = if (chance(0.85)) " ORDER BY $measure $direction$tieBreak" else ""
    return "SELECT user, $measure, $extra FROM events$filter GROUP BY user$order LIMIT ${1 + random.nextInt(10)}"
}

fun oneUserQuery(user: String): String = when (random.nextInt(3)) {
    0 -> "SELECT COUNT(*), SUM(ms), AVG(ms), MIN(page), MAX(page), COUNT(DISTINCT page), MAX(day) FROM events WHERE user = '$user'"
    1 -> "SELECT page, COUNT(*), AVG(items) FROM events WHERE user = '$user' GROUP BY page ORDER BY COUNT(*) DESC LIMIT 5"
    else -> "SELECT day, SUM(ms) FROM events WHERE user = '$user' AND ms IS NOT NULL GROUP BY day ORDER BY SUM(ms) DESC, day LIMIT 3"
}

fun distinctQuery(pickCountry: () -> String): String = when (random.nextInt(6)) {
    0 -> "SELECT country, COUNT(DISTINCT user), COUNT(*) FROM events GROUP BY country"
    5 -> "SELECT day, COUNT(DISTINCT country), COUNT(DISTINCT items) FROM events WHERE country = '${pickCountry()}' GROUP BY day ORDER BY COUNT(DISTINCT items) DESC, day LIMIT 6"
    1 -> "SELECT items, COUNT(DISTINCT page), AVG(ms) FROM events WHERE country <> '${pickCountry()}' GROUP BY items ORDER BY COUNT(DISTINCT page) DESC"
    2 -> "SELECT country, items, COUNT(DISTINCT day) FROM events GROUP BY country, items ORDER BY COUNT(DISTINCT day) DESC, items LIMIT 10"
    3 -> "SELECT COUNT(DISTINCT user), COUNT(DISTINCT ms), COUNT(user) FROM events WHERE items > ${1 + random.nextInt(15)}"
    else -> "SELECT country, COUNT(DISTINCT page) FROM events WHERE day <= ${1 + random.nextInt(365)} GROUP BY country ORDER BY country DESC NULLS FIRST LIMIT 8"
}

fun rangeQuery(pickPage: () -> String, pickUser: () -> String): String {
    val pageLow = pickPage()
    val userLow = pickUser()
    return when (random.nextInt(4)) {
        0 -> "SELECT country, MIN(user), MAX(user), COUNT(*) FROM events WHERE page >= '$pageLow' AND page < '${pickPage()}' GROUP BY country"
        1 -> "SELECT MIN(page), MAX(page), MIN(country) FROM events WHERE user > '$userLow' AND user <= '${pickUser()}'"
        2 -> "SELECT items, MAX(country), MIN(page) FROM events WHERE NOT page < '$pageLow' GROUP BY items ORDER BY items LIMIT 12"
        else -> "SELECT MIN(user), MAX(user), COUNT(DISTINCT country) FROM events WHERE country >= '${pick(listOf("a", "k", "s", ""))}' OR user < '$userLow'"
    }
}

fun miscQuery(pickCountry: () -> String): String = when (random.nextInt(5)) {
    0 -> "SELECT day, COUNT(*), AVG(ms) FROM events GROUP BY day ORDER BY AVG(ms) DESC LIMIT 5"
    1 -> "SELECT country, COUNT(*) FROM events WHERE user IS NULL OR page IS NULL GROUP BY country"
    2 -> "SELECT items, SUM(ms), COUNT(ms) FROM events WHERE country = '${pickCountry()}' AND items IS NOT NULL GROUP BY items"
    3 -> "SELECT COUNT(*), COUNT(country), SUM(items), AVG(day) FROM events WHERE country = '' OR country = 'NULL'"
    else -> "SELECT user, COUNT(*) FROM events WHERE ms > 9990 AND day < 30 GROUP BY user ORDER BY user DESC LIMIT 7"
}

if (largeScale) emitLargeSession() else emitSmallSession()
output.flush()
