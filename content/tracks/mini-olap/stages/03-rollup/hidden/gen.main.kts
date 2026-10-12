import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.random.Random

class Options(val rows: Int, val queries: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--rows" to "80", "--queries" to "120", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        rows = values.getValue("--rows").toInt(),
        queries = values.getValue("--queries").toInt(),
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
        rare.isNotEmpty() && chance(0.1) -> pick(rare)
        else -> common[skewedIndex(common.size)]
    }
}

class NumberPool(private val range: IntRange, private val extremes: List<Int>, private val nullRate: Double) : Pool {
    override fun draw(): Any? = when {
        chance(nullRate) -> null
        extremes.isNotEmpty() && chance(0.06) -> pick(extremes)
        else -> random.nextInt(range.first, range.last + 1)
    }
}

class ColumnModel(val name: String, val type: Type, val pool: Pool)

class TableModel(val name: String, val columns: List<ColumnModel>, val dimensions: List<String>) {
    val numeric: List<ColumnModel> get() = columns.filter { it.type == Type.INT }

    fun column(name: String): ColumnModel = columns.first { it.name == name }
}

val SYMBOL_TOKENS = setOf("(", ")", ",", "*", "=", "<>", "<", "<=", ">", ">=")
val OPERATORS = listOf("=", "<>", "<", "<=", ">", ">=")

fun literal(value: Any?): String = when (value) {
    null -> "NULL"
    is Int -> value.toString()
    is String -> "'$value'"
    else -> error("값이 아니다: $value")
}

fun cellText(value: Any?): String = value?.toString() ?: "\\N"

fun command(tokens: List<String>): String {
    if (largeScale) return tokens.joinToString(" ")
    val text = StringBuilder()
    for ((index, token) in tokens.withIndex()) {
        if (index > 0) {
            val touchesSymbol = token in SYMBOL_TOKENS || tokens[index - 1] in SYMBOL_TOKENS
            val spaces = if (touchesSymbol && chance(0.3)) 0 else if (chance(0.06)) 2 else 1
            repeat(spaces) { text.append(' ') }
        }
        text.append(token)
    }
    return (if (chance(0.03)) "  " else "") + text + (if (chance(0.03)) " " else "")
}

fun listTokens(elements: List<List<String>>): List<String> =
    elements.flatMapIndexed { index, element -> (if (index > 0) listOf(",") else emptyList()) + element }

fun parenthesized(elements: List<String>): List<String> = listOf("(") + listTokens(elements.map { listOf(it) }) + listOf(")")

fun createTableTokens(table: TableModel): List<String> =
    listOf("CREATE", "TABLE", table.name, "(") + listTokens(table.columns.map { listOf(it.name, it.type.name) }) + listOf(")")

fun insertTokens(table: TableModel, count: Int): List<String> =
    listOf("INSERT", "INTO", table.name, "VALUES") +
        listTokens(List(count) { listOf("(") + listTokens(table.columns.map { listOf(literal(it.pool.draw())) }) + listOf(")") })

fun emitCopy(table: TableModel, lines: Int) {
    emit(command(listOf("COPY", table.name, "FROM", "STDIN")))
    repeat(lines) { emit(table.columns.joinToString("\t") { cellText(it.pool.draw()) }) }
    emit("\\.")
}

sealed interface GroupingShape {
    val columns: List<String>

    data object NoGrouping : GroupingShape {
        override val columns: List<String> = emptyList()
    }

    data class ColumnList(override val columns: List<String>) : GroupingShape

    data class Rollup(override val columns: List<String>) : GroupingShape

    data class Cube(override val columns: List<String>) : GroupingShape

    data class Sets(val sets: List<List<String>>, val bareSingles: Boolean) : GroupingShape {
        override val columns: List<String> get() = sets.flatten().distinct()
    }
}

fun groupingTokens(shape: GroupingShape): List<String> = when (shape) {
    GroupingShape.NoGrouping -> emptyList()
    is GroupingShape.ColumnList -> listOf("GROUP", "BY") + listTokens(shape.columns.map { listOf(it) })
    is GroupingShape.Rollup -> listOf("GROUP", "BY", "ROLLUP") + parenthesized(shape.columns)
    is GroupingShape.Cube -> listOf("GROUP", "BY", "CUBE") + parenthesized(shape.columns)
    is GroupingShape.Sets ->
        listOf("GROUP", "BY", "GROUPING", "SETS", "(") +
            listTokens(shape.sets.map { set -> if (shape.bareSingles && set.size == 1) set else parenthesized(set) }) + listOf(")")
}

class SelectShape(
    val items: List<List<String>>,
    val table: String,
    val where: List<String>?,
    val grouping: GroupingShape,
    val orderBy: List<List<String>>,
    val limit: String?,
) {
    fun tokens(): List<String> {
        val tokens = mutableListOf("SELECT")
        tokens += listTokens(items)
        tokens += listOf("FROM", table)
        if (where != null) tokens += listOf("WHERE") + where
        tokens += groupingTokens(grouping)
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

fun comparison(table: TableModel, columns: List<ColumnModel>): List<String> {
    val column = pick(columns)
    return when {
        chance(0.1) -> listOf(column.name, "IS", "NULL")
        chance(0.1) -> listOf(column.name, "IS", "NOT", "NULL")
        else -> listOf(column.name, pick(OPERATORS), literal(if (chance(0.05)) null else column.pool.draw() ?: column.pool.draw()))
    }
}

fun condition(table: TableModel, columns: List<ColumnModel>, depth: Int): List<String> = when {
    depth < 2 && chance(0.3) -> condition(table, columns, depth + 1) + pick(listOf("AND", "OR")) + condition(table, columns, depth + 1)
    depth < 3 && chance(0.12) -> listOf("NOT") + condition(table, columns, depth + 1)
    depth < 3 && chance(0.12) -> listOf("(") + condition(table, columns, depth + 1) + listOf(")")
    else -> comparison(table, columns)
}

fun aggregateItem(table: TableModel, allowDistinct: Boolean = true): List<String> {
    val column = pick(table.columns)
    return when (random.nextInt(12)) {
        0, 1 -> listOf("COUNT", "(", "*", ")")
        2, 3 -> listOf("COUNT", "(", pick(table.numeric).name, ")")
        4, 5 -> listOf("SUM", "(", pick(table.numeric).name, ")")
        6, 7 -> listOf("AVG", "(", pick(table.numeric).name, ")")
        8 -> listOf("MIN", "(", column.name, ")")
        9 -> listOf("MAX", "(", column.name, ")")
        10 -> if (allowDistinct) listOf("COUNT", "(", "DISTINCT", column.name, ")") else listOf("COUNT", "(", column.name, ")")
        else -> listOf("MAX", "(", pick(table.numeric).name, ")")
    }
}

fun randomGrouping(dimensions: List<String>): GroupingShape = when (random.nextInt(10)) {
    0 -> GroupingShape.NoGrouping
    1, 2 -> GroupingShape.ColumnList(pickDistinct(dimensions, 1 + skewedIndex(minOf(3, dimensions.size))))
    3, 4, 5 -> GroupingShape.Rollup(pickDistinct(dimensions, 1 + random.nextInt(minOf(3, dimensions.size))))
    6, 7 -> GroupingShape.Cube(pickDistinct(dimensions, 1 + random.nextInt(minOf(3, dimensions.size))))
    else -> {
        val pool = pickDistinct(dimensions, minOf(dimensions.size, 2 + random.nextInt(2)))
        val candidates = (0 until (1 shl pool.size)).map { mask -> pool.filterIndexed { index, _ -> mask and (1 shl index) != 0 } }
        GroupingShape.Sets(pickDistinct(candidates, 1 + random.nextInt(minOf(4, candidates.size))), bareSingles = chance(0.5))
    }
}

fun randomSelect(table: TableModel, dimensionsOnly: Boolean = false, limitChance: Double = 0.3): SelectShape {
    val grouping = randomGrouping(table.dimensions)
    val items = mutableListOf<List<String>>()
    grouping.columns.filter { chance(0.75) }.forEach { items += listOf(it) }
    grouping.columns.filter { chance(0.3) }.forEach { items += listOf("GROUPING", "(", it, ")") }
    repeat(random.nextInt(if (items.isEmpty()) 1 else 0, 3)) { items += aggregateItem(table) }
    val ordered = if (chance(0.3)) items.shuffled(random) else items
    val whereColumns = if (dimensionsOnly || chance(0.7)) table.dimensions.map(table::column) else table.columns
    val where = if (chance(0.4)) condition(table, whereColumns, 0) else null
    val orderBy = if (chance(0.5)) pickDistinct(ordered, 1 + skewedIndex(3)).map(::orderKey) else emptyList()
    val limit = if (chance(limitChance)) random.nextInt(0, 8).toString() else null
    return SelectShape(ordered, table.name, where, grouping, orderBy, limit)
}

fun aggregateDefinition(name: String, table: TableModel, dimensions: List<String>, measures: List<List<String>>): String =
    command(
        listOf("CREATE", "AGGREGATE", name, "AS", "SELECT") +
            listTokens(dimensions.filter { chance(0.8) }.map { listOf(it) } + measures) +
            listOf("FROM", table.name, "GROUP", "BY") + listTokens(dimensions.map { listOf(it) }),
    )

fun mergeableMeasure(table: TableModel): List<String> {
    val column = pick(table.columns)
    return when (random.nextInt(7)) {
        0, 1 -> listOf("COUNT", "(", "*", ")")
        2 -> listOf("COUNT", "(", pick(table.numeric).name, ")")
        3, 4 -> listOf("SUM", "(", pick(table.numeric).name, ")")
        5 -> listOf("MIN", "(", column.name, ")")
        else -> listOf("MAX", "(", column.name, ")")
    }
}

val salesTable = TableModel(
    "sales",
    listOf(
        ColumnModel("region", Type.STRING, TextPool(listOf("east", "west", "north", ""), listOf("NULL", "East", "서울"), 0.15)),
        ColumnModel("item", Type.STRING, TextPool(listOf("pen", "ink", "pad", "pencil"), listOf("Pen", "잉크"), 0.1)),
        ColumnModel("day", Type.INT, NumberPool(1..4, listOf(0, -1), 0.1)),
        ColumnModel("qty", Type.INT, NumberPool(-2..9, listOf(1_000_000_000, -1_000_000_000), 0.2)),
    ),
    dimensions = listOf("region", "item", "day"),
)

val pairsTable = TableModel(
    "t",
    listOf(
        ColumnModel("a", Type.STRING, TextPool(listOf("x", "y", "z"), listOf("w"), 0.1)),
        ColumnModel("b", Type.INT, NumberPool(1..3, emptyList(), 0.1)),
        ColumnModel("v", Type.INT, NumberPool(0..50, listOf(-7), 0.15)),
    ),
    dimensions = listOf("a", "b"),
)

val emptyTable = TableModel(
    "empty_t",
    listOf(
        ColumnModel("g", Type.STRING, TextPool(listOf("a"), emptyList(), 0.0)),
        ColumnModel("h", Type.INT, NumberPool(1..2, emptyList(), 0.0)),
        ColumnModel("v", Type.INT, NumberPool(1..2, emptyList(), 0.0)),
    ),
    dimensions = listOf("g", "h"),
)

class AggregateDefinition(val name: String, val table: TableModel, val dimensions: List<String>, val measures: List<List<String>>)

class AggregateRegistry {
    private val definitions = mutableListOf<AggregateDefinition>()

    fun all(): List<String> = definitions.map { it.name }

    fun of(table: TableModel): List<AggregateDefinition> = definitions.filter { it.table == table }

    fun add(definition: AggregateDefinition) {
        definitions += definition
    }
}

val registry = AggregateRegistry()
val AGGREGATE_NAMES = listOf("by_region", "by_item", "by_day", "by_region_item", "by_item_day", "by_all", "agg_a", "agg_b", "agg_ab", "agg_e", "agg_eh", "z_last", "a_first")

fun emitAggregate(table: TableModel) {
    if (registry.of(table).size >= 6) return
    val name = AGGREGATE_NAMES.filter { it !in registry.all() }.randomOrNull(random) ?: return
    val dimensions = pickDistinct(table.dimensions, 1 + random.nextInt(table.dimensions.size))
    val measures = List(1 + random.nextInt(4)) { mergeableMeasure(table) }
    emit(aggregateDefinition(name, table, dimensions, measures))
    registry.add(AggregateDefinition(name, table, dimensions, measures))
}

fun answerableSelect(definition: AggregateDefinition): SelectShape {
    val table = definition.table
    val dimensions = pickDistinct(definition.dimensions, random.nextInt(definition.dimensions.size + 1))
    val grouping = when {
        dimensions.isEmpty() -> GroupingShape.NoGrouping
        chance(0.3) -> GroupingShape.Rollup(dimensions)
        chance(0.3) -> GroupingShape.Cube(dimensions)
        chance(0.3) -> GroupingShape.Sets(listOf(dimensions, emptyList()) + dimensions.map { listOf(it) }.take(1), bareSingles = chance(0.5)).let {
            if (it.sets.map { set -> set.toSet() }.distinct().size == it.sets.size) it else GroupingShape.ColumnList(dimensions)
        }
        else -> GroupingShape.ColumnList(dimensions)
    }
    val measures = pickDistinct(definition.measures.distinct(), 1 + random.nextInt(minOf(2, definition.measures.distinct().size)))
    val averages = definition.measures.filter { it[0] == "SUM" && listOf("COUNT", "(", it[2], ")") in definition.measures }
        .filter { chance(0.5) }
        .map { listOf("AVG", "(", it[2], ")") }
    val items = dimensions.filter { chance(0.8) }.map { listOf(it) } +
        dimensions.filter { chance(0.3) }.map { listOf("GROUPING", "(", it, ")") } +
        measures + averages
    val where = if (chance(0.4)) condition(table, definition.dimensions.map(table::column), 0) else null
    val orderBy = if (chance(0.5)) pickDistinct(items, 1 + skewedIndex(2)).map(::orderKey) else emptyList()
    val limit = if (chance(0.3)) random.nextInt(0, 6).toString() else null
    return SelectShape(items, table.name, where, grouping, orderBy, limit)
}

fun querySelect(table: TableModel): SelectShape {
    val definitions = registry.of(table)
    return if (definitions.isNotEmpty() && chance(0.45)) answerableSelect(pick(definitions)) else randomSelect(table)
}

fun faultyAggregate(): String {
    val table = pick(listOf(salesTable, pairsTable))
    val dimension = pick(table.dimensions)
    val existing = registry.all().randomOrNull(random) ?: "sales"
    val head = listOf("CREATE", "AGGREGATE", "bad_agg", "AS", "SELECT")
    return command(
        when (random.nextInt(14)) {
            13 -> head + listOf("MIN", "(", "city", ")", ",", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", dimension)
            0 -> head + listOf(dimension, ",", "AVG", "(", pick(table.numeric).name, ")", "FROM", table.name, "GROUP", "BY", dimension)
            1 -> head + listOf(dimension, ",", "COUNT", "(", "DISTINCT", pick(table.columns).name, ")", "FROM", table.name, "GROUP", "BY", dimension)
            2 -> listOf("CREATE", "AGGREGATE", existing, "AS", "SELECT", dimension, ",", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", dimension)
            3 -> listOf("CREATE", "AGGREGATE", table.name, "AS", "SELECT", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", dimension)
            4 -> head + listOf("COUNT", "(", "*", ")", "FROM", table.name, "WHERE", dimension, "IS", "NULL", "GROUP", "BY", dimension)
            5 -> head + listOf("SUM", "(", pick(table.numeric).name, ")", "FROM", table.name)
            6 -> head + listOf("COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", "ROLLUP", "(", dimension, ")")
            7 -> head + listOf(dimension, ",", "GROUPING", "(", dimension, ")", "FROM", table.name, "GROUP", "BY", dimension)
            8 -> head + listOf(pick(table.columns.filter { it.name != dimension }).name, "FROM", table.name, "GROUP", "BY", dimension)
            9 -> head + listOf("SUM", "(", table.columns.first { it.type == Type.STRING }.name, ")", "FROM", table.name, "GROUP", "BY", dimension)
            10 -> head + listOf("COUNT", "(", "*", ")", "FROM", existing, "GROUP", "BY", dimension)
            11 -> head + listOf("COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", dimension, "ORDER", "BY", "COUNT", "(", "*", ")")
            else -> head + listOf("MIN", "(", "city", ")", ",", "AVG", "(", pick(table.numeric).name, ")", "FROM", table.name, "GROUP", "BY", dimension, "LIMIT", "1")
        },
    )
}

fun faultySelect(table: TableModel): String {
    val shape = randomSelect(table)
    val dimension = pick(table.dimensions)
    val other = pick(table.dimensions - dimension)
    val head = listOf("SELECT", "COUNT", "(", "*", ")", "FROM", table.name)
    return command(
        when (random.nextInt(15)) {
            14 -> listOf("SELECT", dimension, ",", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", "ROLLUP", "(", dimension, ")", "ORDER", "BY", "GROUPING", "(", dimension, ")")
            0 -> head + listOf("GROUP", "BY", "GROUPING", "SETS", "(", "(", dimension, ")", ",", "(", dimension, ")", ")")
            1 -> head + listOf("GROUP", "BY", "GROUPING", "SETS", "(", "(", dimension, ",", other, ")", ",", "(", other, ",", dimension, ")", ")")
            2 -> head + listOf("GROUP", "BY", "ROLLUP", "(", dimension, ",", dimension, ")")
            3 -> head + listOf("GROUP", "BY", dimension, ",", "ROLLUP", "(", other, ")")
            4 -> head + listOf("GROUP", "BY", "ROLLUP", "(", dimension, ")", ",", "CUBE", "(", other, ")")
            5 -> head + listOf("GROUP", "BY", "ROLLUP", "(", ")")
            6 -> head + listOf("GROUP", "BY", "GROUPING", "SETS", "(", ")")
            7 -> head + listOf("GROUP", "BY", "GROUPING", "(", dimension, ")")
            8 -> listOf("SELECT", "GROUPING", "(", other, ")", ",", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", "ROLLUP", "(", dimension, ")")
            9 -> listOf("SELECT", "GROUPING", "(", dimension, ")", "FROM", table.name)
            10 -> listOf("SELECT", "GROUPING", dimension, "FROM", table.name, "GROUP", "BY", dimension)
            11 -> head + listOf("GROUP", "BY", "GROUPING", "SETS", "(", dimension, ",", dimension, ")")
            12 -> listOf("SELECT", dimension, ",", "COUNT", "(", "*", ")", "FROM", table.name, "GROUP", "BY", "CUBE", "(", other, ")", "ORDER", "BY", "GROUPING", "(", dimension, ")")
            else -> shape.tokens().map { if (it == table.name) pick(registry.all().ifEmpty { listOf("nope") }) else it }
        },
    )
}

fun emitNameClash() {
    val existing = registry.all().randomOrNull(random) ?: return
    when (random.nextInt(4)) {
        0 -> emit(command(listOf("CREATE", "TABLE", existing, "(", "a", "INT", ")")))
        1 -> emit(command(listOf("INSERT", "INTO", existing, "VALUES", "(", "1", ")")))
        2 -> {
            emit(command(listOf("COPY", existing, "FROM", "STDIN")))
            emit("x\t1")
            emit("\\.")
        }
        else -> emit(command(listOf("EXPLAIN", "SELECT", "COUNT", "(", "*", ")", "FROM", existing)))
    }
}

fun emitExplain(table: TableModel) {
    emit(command(listOf("EXPLAIN") + querySelect(table).tokens()))
}

fun emitSmallSession() {
    emit(command(createTableTokens(salesTable)))
    emit(command(createTableTokens(pairsTable)))
    emit(command(createTableTokens(emptyTable)))
    repeat(2) { emitAggregate(salesTable) }
    emitAggregate(emptyTable)
    var loaded = 0
    var queries = 0
    while (queries < options.queries || loaded < options.rows) {
        val roll = random.nextInt(100)
        val table = if (chance(0.7)) salesTable else pairsTable
        when {
            loaded < options.rows && roll < 12 -> {
                val count = 1 + random.nextInt(6)
                if (chance(0.5)) emit(command(insertTokens(table, count))) else emitCopy(table, count)
                loaded += count
            }
            roll < 17 -> emitAggregate(table)
            roll < 21 -> emit(faultyAggregate())
            roll < 24 -> emitNameClash()
            roll < 32 -> {
                emit(faultySelect(table))
                queries++
            }
            roll < 48 -> {
                emitExplain(table)
                queries++
            }
            roll < 53 -> {
                emit(command(querySelect(emptyTable).tokens()))
                queries++
            }
            else -> {
                emit(command(querySelect(table).tokens()))
                queries++
            }
        }
    }
}

val REGIONS = listOf("kr", "us", "jp", "de", "fr", "br", "in", "cn", "gb", "ca", "au", "", "NULL")
val CHANNELS = listOf("web", "app", "shop", "phone", "partner", "Web")
val PRODUCTS = List(400) { index -> if (index < 6) "상품$index" else "p" + index.toString(36).padStart(3, '0') }
val CUSTOMERS = List(20_000) { index -> "c" + (index * 7_919L % 46_656L).toInt().toString(36).padStart(3, '0') + index % 10 }

val ordersTable = TableModel(
    "orders",
    listOf(
        ColumnModel("region", Type.STRING, TextPool(REGIONS, emptyList(), 0.02)),
        ColumnModel("channel", Type.STRING, TextPool(CHANNELS, emptyList(), 0.02)),
        ColumnModel("product", Type.STRING, TextPool(PRODUCTS, emptyList(), 0.01)),
        ColumnModel("day", Type.INT, NumberPool(1..90, emptyList(), 0.01)),
        ColumnModel("qty", Type.INT, NumberPool(1..50, emptyList(), 0.03)),
        ColumnModel("amount", Type.INT, NumberPool(100..99_999, emptyList(), 0.04)),
        ColumnModel("customer", Type.STRING, TextPool(CUSTOMERS, emptyList(), 0.02)),
    ),
    dimensions = listOf("region", "channel", "product", "day"),
)

class ScaleAggregate(val name: String, val dimensions: List<String>, val measures: List<String>)

val SCALE_AGGREGATES = listOf(
    ScaleAggregate("by_region_channel", listOf("region", "channel"), listOf("COUNT(*)", "SUM(qty)", "COUNT(qty)", "MIN(amount)", "MAX(amount)")),
    ScaleAggregate("by_product", listOf("product"), listOf("COUNT(*)", "SUM(amount)", "COUNT(amount)", "MAX(qty)")),
    ScaleAggregate("by_region_day", listOf("region", "day"), listOf("COUNT(*)", "SUM(qty)", "SUM(amount)", "COUNT(amount)")),
    ScaleAggregate("by_channel_day_region", listOf("channel", "day", "region"), listOf("COUNT(*)", "SUM(amount)", "COUNT(amount)", "MIN(qty)", "SUM(qty)")),
)

fun createScaleAggregate(aggregate: ScaleAggregate): String =
    "CREATE AGGREGATE ${aggregate.name} AS SELECT ${(aggregate.dimensions + aggregate.measures).joinToString(", ")} FROM orders GROUP BY ${aggregate.dimensions.joinToString(", ")}"

fun scaleGrouping(dimensions: List<String>): String = when (random.nextInt(8)) {
    0, 1, 2 -> " GROUP BY ROLLUP(${dimensions.joinToString(", ")})"
    3, 4, 5 -> " GROUP BY CUBE(${dimensions.joinToString(", ")})"
    6 -> " GROUP BY GROUPING SETS (${dimensions.joinToString(", ") { "($it)" }}, ())"
    else -> " GROUP BY ${dimensions.joinToString(", ")}"
}

fun scaleFilter(dimensions: List<String>): String {
    val column = pick(dimensions)
    return when {
        !chance(0.35) -> ""
        column == "day" -> " WHERE day >= ${1 + random.nextInt(60)} AND day <= ${30 + random.nextInt(61)}"
        column == "region" -> " WHERE region ${pick(listOf("=", "<>", ">="))} '${pick(REGIONS)}'"
        column == "channel" -> " WHERE channel ${pick(listOf("=", "<>"))} '${pick(CHANNELS)}' OR channel IS NULL"
        else -> " WHERE product < '${pick(PRODUCTS)}'"
    }
}

fun answerableQuery(): String {
    val aggregate = pick(SCALE_AGGREGATES)
    val dimensions = pickDistinct(aggregate.dimensions, 1 + random.nextInt(aggregate.dimensions.size))
    val measures = pickDistinct(aggregate.measures, 1 + random.nextInt(2)).map { measure ->
        if (measure.startsWith("SUM(") && chance(0.3) && "COUNT(${measure.substring(4)}" in aggregate.measures) "AVG(${measure.substring(4)}" else measure
    }
    val shown = dimensions.filter { chance(0.85) }
    val markers = dimensions.filter { chance(0.35) }.map { "GROUPING($it)" }
    val items = shown + markers + measures
    val order = if (chance(0.6)) {
        " ORDER BY " + pickDistinct(items, 1 + random.nextInt(2)).joinToString(", ") { it + pick(listOf("", " DESC", " ASC NULLS FIRST", " DESC NULLS LAST")) }
    } else {
        ""
    }
    return "SELECT ${items.joinToString(", ")} FROM orders${scaleFilter(dimensions)}${scaleGrouping(dimensions)}$order LIMIT ${1 + random.nextInt(12)}"
}

fun tableQuery(): String = when (random.nextInt(5)) {
    0 -> "SELECT region, COUNT(DISTINCT customer), COUNT(*) FROM orders GROUP BY ROLLUP(region) LIMIT 6"
    1 -> "SELECT channel, SUM(amount), AVG(qty) FROM orders WHERE qty > ${30 + random.nextInt(20)} GROUP BY CUBE(channel) ORDER BY SUM(amount) DESC LIMIT 4"
    2 -> "SELECT product, MIN(customer), MAX(amount) FROM orders WHERE customer = '${pick(CUSTOMERS)}' GROUP BY product LIMIT 5"
    3 -> "SELECT region, day, COUNT(*) FROM orders WHERE amount < ${100 + random.nextInt(2_000)} GROUP BY ROLLUP(region, day) ORDER BY COUNT(*) DESC LIMIT 5"
    else -> "SELECT customer, COUNT(*), SUM(amount) FROM orders WHERE region = '${pick(REGIONS)}' AND day = ${1 + random.nextInt(90)} GROUP BY customer ORDER BY SUM(amount) DESC LIMIT 3"
}

fun emitLargeSession() {
    emit(command(createTableTokens(ordersTable)))
    emit(createScaleAggregate(SCALE_AGGREGATES[0]))
    emit(createScaleAggregate(SCALE_AGGREGATES[1]))
    emit("COPY orders FROM STDIN")
    val line = StringBuilder()
    repeat(options.rows) {
        line.setLength(0)
        ordersTable.columns.forEachIndexed { index, column ->
            if (index > 0) line.append('\t')
            line.append(cellText(column.pool.draw()))
        }
        emit(line.toString())
    }
    emit("\\.")
    emit(createScaleAggregate(SCALE_AGGREGATES[2]))
    emit(createScaleAggregate(SCALE_AGGREGATES[3]))
    repeat(options.queries) {
        val roll = random.nextInt(1000)
        when {
            roll < 25 -> emit(command(insertTokens(ordersTable, 1 + random.nextInt(3))))
            roll < 33 -> emit(tableQuery())
            roll < 350 -> emit("EXPLAIN " + answerableQuery())
            else -> emit(answerableQuery())
        }
    }
}

if (largeScale) emitLargeSession() else emitSmallSession()
output.flush()
