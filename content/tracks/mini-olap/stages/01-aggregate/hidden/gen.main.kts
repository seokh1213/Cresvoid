import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.random.Random

class Options(val rows: Int, val selects: Int, val distinct: Int, val batch: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--rows" to "70", "--selects" to "90", "--distinct" to "8", "--batch" to "5", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        rows = values.getValue("--rows").toInt(),
        selects = values.getValue("--selects").toInt(),
        distinct = values.getValue("--distinct").toInt(),
        batch = values.getValue("--batch").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.rows >= 10_000
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

fun itemNames(count: Int): List<String> {
    val base = listOf("pen", "ink", "pencil", "pe", "Pen", "paper", "잉크", "pen2", "box", "tape")
    if (count <= base.size) return base.take(count)
    return base + (base.size until count).map { "it" + it.toString(36).padStart(4, '0') }
}

val salesTable = TableModel(
    "sales",
    listOf(
        ColumnModel("region", Type.STRING, TextPool(listOf("east", "west", "north", "south", "서울", ""), ODD_TEXTS, 0.12)),
        ColumnModel("item", Type.STRING, TextPool(itemNames(options.distinct), ODD_TEXTS.take(4), 0.08)),
        ColumnModel("qty", Type.INT, NumberPool(-3..12, listOf(0, -1, 100), 0.15)),
        ColumnModel("price", Type.INT, NumberPool(-50..400, INT_EXTREMES, 0.12)),
        ColumnModel("day", Type.INT, NumberPool(1..(if (largeScale) 365 else 7), emptyList(), 0.05)),
    ),
)

val visitsTable = TableModel(
    "visits",
    listOf(
        ColumnModel("user", Type.STRING, TextPool((1..(if (largeScale) 2_000 else 6)).map { "u$it" }, listOf("U1", "u1 ", "", "NULL"), 0.1)),
        ColumnModel("page", Type.STRING, TextPool(listOf("/home", "/cart", "/a", "/b", "/검색", "/home/x"), listOf("", "/"), 0.1)),
        ColumnModel("ms", Type.INT, NumberPool(0..900, listOf(-5, 1_000_000_000), 0.2)),
        ColumnModel("count", Type.INT, NumberPool(-2..3, emptyList(), 0.3)),
    ),
)

val emptyTable = TableModel(
    "empty_t",
    listOf(
        ColumnModel("g", Type.STRING, TextPool(listOf("a"), emptyList(), 0.0)),
        ColumnModel("v", Type.INT, NumberPool(0..1, emptyList(), 0.0)),
    ),
)

val tables = listOf(salesTable, visitsTable)
val UNKNOWN_TABLES = listOf("nope", "sale", "sales2", "visit")
val UNKNOWN_COLUMNS = listOf("city", "zip", "qty2", "regionx", "total")
val OPERATORS = listOf("=", "<>", "<", "<=", ">", ">=")
val SYMBOL_TOKENS = setOf("(", ")", ",", "*", "=", "<>", "<", "<=", ">", ">=")

fun literal(value: Any?): String = when (value) {
    null -> "NULL"
    is Int -> value.toString()
    is String -> "'$value'"
    else -> error("값이 아니다: $value")
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

fun emitNoise() {
    if (!largeScale && chance(0.04)) emit(if (chance(0.5)) "" else "   ")
}

fun createTableTokens(table: TableModel): List<String> =
    listOf("CREATE", "TABLE", table.name, "(") +
        table.columns.flatMapIndexed { index, column ->
            (if (index > 0) listOf(",") else emptyList()) + listOf(column.name, column.type.name)
        } + listOf(")")

fun rowTokens(table: TableModel): List<String> =
    listOf("(") + table.columns.flatMapIndexed { index, column ->
        (if (index > 0) listOf(",") else emptyList()) + literal(column.pool.draw())
    } + listOf(")")

fun insertTokens(table: TableModel, rows: List<List<String>>): List<String> =
    listOf("INSERT", "INTO", table.name, "VALUES") + rows.flatMapIndexed { index, row ->
        (if (index > 0) listOf(",") else emptyList()) + row
    }

fun insertRows(table: TableModel, count: Int) {
    emit(command(insertTokens(table, List(count) { rowTokens(table) })))
}

fun wrongType(column: ColumnModel): String =
    if (column.type == Type.INT) literal(pick(listOf("1", "east", ""))) else literal(random.nextInt(-5, 50))

fun faultyInsert() {
    val table = pick(tables)
    val good = { rowTokens(table) }
    val shortRow = listOf("(") + table.columns.drop(1).flatMapIndexed { index, column ->
        (if (index > 0) listOf(",") else emptyList()) + literal(column.pool.draw())
    } + listOf(")")
    val longRow = good().dropLast(1) + listOf(",", literal(pick(table.columns).pool.draw()), ")")
    val typedRow: List<String> = run {
        val broken = random.nextInt(table.columns.size)
        listOf("(") + table.columns.flatMapIndexed { index, column ->
            (if (index > 0) listOf(",") else emptyList()) + if (index == broken) wrongType(column) else literal(column.pool.draw())
        } + listOf(")")
    }
    val line = when (random.nextInt(14)) {
        0 -> command(listOf("INSERT", "INTO", pick(UNKNOWN_TABLES), "VALUES") + good())
        1 -> command(insertTokens(table, listOf(good(), shortRow)))
        2 -> command(insertTokens(table, listOf(longRow)))
        3 -> command(insertTokens(table, listOf(good(), typedRow, good())))
        4 -> command(insertTokens(table, listOf(typedRow, shortRow)))
        5 -> command(insertTokens(table, listOf(shortRow, typedRow)))
        6 -> command(insertTokens(table, listOf(good()))).replaceFirst("VALUES", "values")
        7 -> command(insertTokens(table, listOf(good(), good()))) + ","
        8 -> command(insertTokens(table, listOf(good()))).replaceFirst(" INTO", "")
        9 -> "INSERT INTO ${table.name} VALUES (" + table.columns.joinToString(", ") { badLiteral(it) } + ")"
        10 -> command(insertTokens(table, listOf(good()))) + ";"
        11 -> "INSERT INTO ${table.name} VALUES ()"
        12 -> "INSERT INTO ${table.name} VALUES " + good().joinToString(" ") + " " + good().joinToString(" ")
        else -> command(listOf("INSERT", "INTO", emptyTable.name, "VALUES", "(", "'a'", ",", "'1'", ")"))
    }
    emit(line)
}

fun badLiteral(column: ColumnModel): String = when (column.type) {
    Type.INT -> pick(listOf("007", "-0", "1000000001", "-1000000001", "1e3", "12345678901234567890"))
    Type.STRING -> pick(listOf("'it''s'", "'abc", "'a\\b'", "\"east\"", "'tab\there'", "'é'"))
}

fun aggregateItem(table: TableModel): List<String> {
    val column = pick(table.columns)
    return when (random.nextInt(9)) {
        0, 1 -> listOf("COUNT", "(", "*", ")")
        2 -> listOf("COUNT", "(", column.name, ")")
        3 -> listOf("SUM", "(", pick(table.numeric).name, ")")
        4, 5 -> listOf("AVG", "(", pick(table.numeric).name, ")")
        6 -> listOf("MIN", "(", column.name, ")")
        7 -> listOf("MAX", "(", column.name, ")")
        else -> listOf("SUM", "(", pick(table.numeric).name, ")")
    }
}

fun comparison(table: TableModel): List<String> {
    val column = pick(table.columns)
    return when {
        chance(0.12) -> listOf(column.name, "IS", "NULL")
        chance(0.12) -> listOf(column.name, "IS", "NOT", "NULL")
        else -> {
            val value = if (chance(0.07)) null else column.pool.draw() ?: column.pool.draw()
            listOf(column.name, pick(OPERATORS), literal(value))
        }
    }
}

fun condition(table: TableModel, depth: Int): List<String> = when {
    depth < 2 && chance(0.32) -> condition(table, depth + 1) + pick(listOf("AND", "OR")) + condition(table, depth + 1)
    depth < 3 && chance(0.14) -> listOf("NOT") + condition(table, depth + 1)
    depth < 3 && chance(0.14) -> listOf("(") + condition(table, depth + 1) + listOf(")")
    else -> comparison(table)
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
        items.forEachIndexed { index, item -> if (index > 0) tokens += ","; tokens += item }
        tokens += listOf("FROM", table)
        if (where != null) tokens += listOf("WHERE") + where
        if (groupBy.isNotEmpty()) {
            tokens += listOf("GROUP", "BY")
            groupBy.forEachIndexed { index, column -> if (index > 0) tokens += ","; tokens += column }
        }
        if (orderBy.isNotEmpty()) {
            tokens += listOf("ORDER", "BY")
            orderBy.forEachIndexed { index, key -> if (index > 0) tokens += ","; tokens += key }
        }
        if (limit != null) tokens += listOf("LIMIT", limit)
        return tokens
    }
}

fun groupableColumns(table: TableModel): List<ColumnModel> =
    table.columns.filter { it.name != "price" && it.name != "ms" && !(largeScale && it.name == "item") }

fun randomSelect(table: TableModel, wideGroup: ColumnModel? = null, unlimited: Boolean = false): SelectShape {
    val groupColumns = when {
        wideGroup != null -> listOf(wideGroup) + if (chance(0.15)) pickDistinct(groupableColumns(table) - wideGroup, 1) else emptyList()
        chance(0.6) -> pickDistinct(groupableColumns(table), 1 + skewedIndex(3))
        else -> emptyList()
    }
    val items = mutableListOf<List<String>>()
    groupColumns.filter { chance(0.8) }.forEach { items += listOf(it.name) }
    repeat(random.nextInt(if (items.isEmpty()) 1 else 0, 4)) { items += aggregateItem(table) }
    if (chance(0.06) && items.isNotEmpty()) items += pick(items)
    val shuffled = if (chance(0.3)) items.shuffled(random) else items
    val where = if (chance(0.5)) condition(table, 0) else null
    val orderBy = if (chance(0.5)) pickDistinct(shuffled, 1 + skewedIndex(3)).map(::orderKey) else emptyList()
    val limit = when {
        unlimited -> null
        wideGroup != null -> (1 + random.nextInt(20)).toString()
        largeScale && groupColumns.isNotEmpty() && chance(0.75) -> (1 + random.nextInt(30)).toString()
        chance(0.3) -> random.nextInt(0, 6).toString()
        else -> null
    }
    return SelectShape(shuffled, table.name, where, groupColumns.map { it.name }, orderBy, limit)
}

enum class Fault { NO_TABLE, NO_COLUMN, TYPE, NOT_GROUPED, NOT_SELECTED }

fun faultySelect(table: TableModel, faults: Set<Fault>): SelectShape {
    val base = randomSelect(table)
    var items = base.items
    var where = base.where
    var groupBy = base.groupBy
    var orderBy = base.orderBy
    var tableName = base.table
    if (Fault.NO_TABLE in faults) tableName = pick(UNKNOWN_TABLES)
    if (Fault.NO_COLUMN in faults) {
        val unknown = pick(UNKNOWN_COLUMNS)
        when (random.nextInt(4)) {
            0 -> items = items + listOf(listOf("MIN", "(", unknown, ")"))
            1 -> where = (where?.plus("AND") ?: emptyList()) + listOf(unknown, "=", "1")
            2 -> groupBy = groupBy + unknown
            else -> orderBy = orderBy + listOf(listOf(unknown))
        }
        if (chance(0.3)) items = listOf(listOf(pick(UNKNOWN_COLUMNS))) + items
    }
    if (Fault.TYPE in faults) {
        val text = table.columns.first { it.type == Type.STRING }
        val number = pick(table.numeric)
        when (random.nextInt(4)) {
            0 -> items = items + listOf(listOf(pick(listOf("SUM", "AVG")), "(", text.name, ")"))
            1 -> where = listOf(number.name, pick(OPERATORS), literal(pick(listOf("1", "x"))))
            2 -> where = listOf("NOT", text.name, pick(OPERATORS), random.nextInt(0, 9).toString())
            else -> orderBy = orderBy + listOf(listOf("AVG", "(", text.name, ")"))
        }
    }
    if (Fault.NOT_GROUPED in faults) {
        val outside = table.columns.filter { it.name !in groupBy }
        if (outside.isNotEmpty()) items = items + listOf(listOf(pick(outside).name))
    }
    if (Fault.NOT_SELECTED in faults) {
        val missing = listOf(listOf("COUNT", "(", "*", ")"), listOf("MAX", "(", table.columns.first().name, ")"))
            .filter { it !in items }
        if (missing.isNotEmpty()) orderBy = orderBy + listOf(orderKey(pick(missing)))
    }
    return SelectShape(items, tableName, where, groupBy, orderBy, base.limit)
}

fun emitSelect(shape: SelectShape) {
    emit(command(shape.tokens()))
}

fun syntaxBrokenSelect(table: TableModel): String {
    val shape = randomSelect(table)
    val tokens = shape.tokens()
    val column = pick(table.columns)
    return when (random.nextInt(20)) {
        0 -> command(tokens.map { if (it == "FROM") "FORM" else it })
        1 -> command(tokens) + ";"
        2 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "WHERE", column.name, "!=", literal(column.pool.draw())))
        3 -> command(tokens.map { if (it == table.name) table.name.replaceFirstChar(Char::uppercase) else it })
        4 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", column.name))
        5 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", column.name, ",", column.name))
        6 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "WHERE", "5", "<", pick(table.numeric).name))
        7 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "WHERE", pick(table.numeric).name, "=", pick(table.numeric).name))
        8 -> command(listOf("SELECT", "SUM", "(", "*", ")", "FROM", table.name))
        9 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "LIMIT", pick(listOf("-1", "007", "-0", "1.5"))))
        10 -> command(listOf("SELECT", "FROM", table.name))
        11 -> command(listOf("SELECT", "COUNT", "(", "*", ")", ",", "FROM", table.name))
        12 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "WHERE", column.name, "IS", "NOT"))
        13 -> "SELECT COUNT(*) FROM ${table.name} WHERE ${column.name} = \"x\""
        14 -> "SELECT COUNT(*) FROM ${table.name}\tWHERE ${column.name} IS NULL"
        15 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "ORDER", "BY", "COUNT", "(", "*", ")", "NULLS"))
        16 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "WHERE", column.name, "=", badLiteral(column)))
        17 -> command(listOf("SELECT", "COUNT", "(", "qty", ",", "price", ")", "FROM", table.name))
        18 -> command(listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "ORDER", "BY", "COUNT", "(", "*", ")", "DESC", "ASC"))
        else -> command(listOf("SELECT", "COUNT", "(", "*", ")", "from", table.name))
    }
}

fun unknownCommand(): String {
    val table = pick(tables)
    return pick(
        listOf(
            "select COUNT(*) FROM ${table.name}",
            "Select COUNT(*) FROM ${table.name}",
            "SELECT; COUNT(*) FROM ${table.name}",
            "INSERT2 INTO ${table.name} VALUES (1)",
            "CREATE_TABLE t9 (a INT)",
            "SELECT\tCOUNT(*) FROM ${table.name}",
            "sElEcT COUNT(*) FROM ${table.name}",
            "insert into ${table.name} values (1)",
            "SELECT-COUNT(*) FROM ${table.name}",
            "Ｓelect",
        ),
    )
}

fun faultyCreate(): String = pick(
    listOf(
        command(createTableTokens(pick(tables))),
        "CREATE TABLE t2 (a INT, a STRING)",
        "CREATE TABLE t2 (a INT, b TEXT)",
        "CREATE TABLE t2 a INT",
        "CREATE TABLE t2 ()",
        "CREATE TABLE T2 (a INT)",
        "CREATE TABLE t2 (a INT,)",
        "CREATE TABLE t2 (A INT)",
        "CREATE TABLE",
        "CREATE",
        "CREATE TABLE t2 (a INT b STRING)",
        "CREATE TABLE t2 (a)",
    ),
)

fun emitSmallSession() {
    val budget = options.rows
    var inserted = 0
    emit(command(createTableTokens(salesTable)))
    if (chance(0.7)) emit(command(listOf("SELECT", "COUNT", "(", "*", ")", ",", "SUM", "(", "qty", ")", ",", "AVG", "(", "price", ")", ",", "MIN", "(", "region", ")", "FROM", "sales")))
    emit(command(createTableTokens(visitsTable)))
    emit(command(createTableTokens(emptyTable)))
    var selects = 0
    while (selects < options.selects || inserted < budget) {
        emitNoise()
        val roll = random.nextInt(100)
        when {
            inserted < budget && roll < 30 -> {
                val count = 1 + random.nextInt(options.batch)
                insertRows(if (chance(0.7)) salesTable else visitsTable, count)
                inserted += count
            }
            roll < 38 -> faultyInsert()
            roll < 41 -> emit(faultyCreate())
            roll < 44 -> emit(unknownCommand())
            roll < 52 -> {
                emit(syntaxBrokenSelect(pick(tables)))
                selects++
            }
            roll < 64 -> {
                val faults = Fault.entries.filter { chance(0.3) }.toSet().ifEmpty { setOf(pick(Fault.entries)) }
                emitSelect(faultySelect(pick(tables), faults))
                selects++
            }
            roll < 67 -> {
                emitSelect(randomSelect(emptyTable))
                selects++
            }
            else -> {
                emitSelect(randomSelect(if (chance(0.7)) salesTable else visitsTable))
                selects++
            }
        }
    }
}

fun emitLargeSession() {
    emit(command(createTableTokens(salesTable)))
    emit(command(createTableTokens(visitsTable)))
    val visitsRows = options.rows / 5
    val salesRows = options.rows - visitsRows
    val wideItems = itemNames(options.distinct)
    var salesInserted = 0
    var visitsInserted = 0
    var warmupSelects = 0
    while (salesInserted < salesRows || visitsInserted < visitsRows) {
        val loadSales = salesInserted < salesRows && (visitsInserted >= visitsRows || chance(0.8))
        val table = if (loadSales) salesTable else visitsTable
        val remaining = if (loadSales) salesRows - salesInserted else visitsRows - visitsInserted
        val count = minOf(remaining, options.batch - random.nextInt(options.batch / 4 + 1))
        val rows = List(count) { index ->
            if (loadSales && salesInserted + index < wideItems.size) {
                rowTokens(table).toMutableList().also { it[3] = literal(wideItems[salesInserted + index]) }
            } else {
                rowTokens(table)
            }
        }
        emit(command(insertTokens(table, rows)))
        if (loadSales) salesInserted += count else visitsInserted += count
        if (chance(0.05) && warmupSelects < 10) {
            emitSelect(randomSelect(table))
            warmupSelects++
        }
    }
    val item = salesTable.columns.first { it.name == "item" }
    val unlimitedQuery = random.nextInt(options.selects)
    repeat(options.selects) { index ->
        val roll = random.nextInt(100)
        when {
            index == unlimitedQuery -> emitSelect(randomSelect(salesTable, wideGroup = item, unlimited = true))
            roll < 30 -> emitSelect(randomSelect(salesTable, wideGroup = item))
            roll < 50 -> {
                val target = literal(item.pool.draw() ?: "pen")
                emit(command(listOf("SELECT", "COUNT", "(", "*", ")", ",", "SUM", "(", "qty", ")", ",", "AVG", "(", "price", ")", ",", "MIN", "(", "region", ")", ",", "MAX", "(", "day", ")", "FROM", "sales", "WHERE", "item", "=", target)))
            }
            roll < 76 -> emitSelect(randomSelect(salesTable))
            roll < 92 -> emitSelect(randomSelect(visitsTable))
            roll < 96 -> emitSelect(faultySelect(salesTable, setOf(pick(Fault.entries))))
            else -> emit(syntaxBrokenSelect(salesTable))
        }
    }
}

if (largeScale) emitLargeSession() else emitSmallSession()
output.flush()
