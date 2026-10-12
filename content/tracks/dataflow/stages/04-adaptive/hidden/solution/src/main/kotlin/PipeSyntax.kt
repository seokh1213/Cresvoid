private const val PARTITION_LIMIT = 1000L
private const val OPERAND_LIMIT = 1_000_000_000L
private const val PIPE_SEPARATOR = "|"
private const val NULL_SAFE = "nullsafe"

class PipeDefinition(val name: String, val transforms: List<Transform>) {
    fun referencedNames(): List<String> = transforms.mapNotNull { transform ->
        when (transform) {
            is Source -> transform.name
            is Join -> transform.name
            is UnaryTransform -> null
        }
    }
}

fun parsePipe(tokens: List<String>): PipeDefinition {
    if (tokens.size < 4 || !isName(tokens[1]) || tokens[2] != "=") syntaxError()
    val transforms = piecesOf(tokens.drop(3)).map { parseTransform(it) ?: syntaxError() }
    if (transforms.first() !is Source || transforms.drop(1).any { it is Source }) syntaxError()
    return PipeDefinition(tokens[1], transforms)
}

private fun piecesOf(tokens: List<String>): List<List<String>> {
    val pieces = mutableListOf<List<String>>()
    var pieceStart = 0
    for (index in 0..tokens.size) {
        if (index == tokens.size || tokens[index] == PIPE_SEPARATOR) {
            if (index == pieceStart) syntaxError()
            pieces += tokens.subList(pieceStart, index)
            pieceStart = index + 1
        }
    }
    return pieces
}

private fun parseTransform(tokens: List<String>): Transform? = when (tokens.first()) {
    "source" -> tokens.takeIf { it.size == 2 && isName(it[1]) }?.let { Source(it[1]) }
    "map" -> parseMap(tokens)
    "filter" -> parseFilter(tokens)
    "reduceByKey" -> parseReduceByKey(tokens)
    "groupByKey" -> partitionsArgument(tokens)?.let(::GroupByKey)
    "repartition" -> partitionsArgument(tokens)?.let(::Repartition)
    "coalesce" -> partitionsArgument(tokens)?.let(::Coalesce)
    "join" -> parseJoin(tokens)
    else -> null
}

private fun parseMap(tokens: List<String>): Transform? {
    if (tokens.size == 4 && tokens[1] == "key" && tokens[2] == "prefix") {
        return integerIn(tokens[3], 1L..KEY_MAX_LENGTH)?.let { MapKeyPrefix(it.toInt()) }
    }
    if (tokens.size < 3 || tokens[1] != "value") return null
    val arithmetic = Arithmetic.entries.firstOrNull { it.token == tokens[2] }
    if (arithmetic != null) {
        return tokens.takeIf { it.size == 4 }?.let { operandArgument(it[3]) }?.let { MapValueArithmetic(arithmetic, it) }
    }
    val summary = Summary.entries.firstOrNull { it.token == tokens[2] } ?: return null
    return MapValueSummary(summary).takeIf { tokens.size == 3 }
}

private fun parseFilter(tokens: List<String>): Transform? {
    if (tokens.size != 4) return null
    return when (tokens[1]) {
        "key" -> when {
            !isKey(tokens[3]) -> null
            tokens[2] == "=" -> FilterKeyEquals(tokens[3])
            tokens[2] == "!=" -> FilterKeyDiffers(tokens[3])
            else -> null
        }
        "value" -> {
            val comparison = Comparison.entries.firstOrNull { it.symbol == tokens[2] }
            val operand = operandArgument(tokens[3])
            if (comparison == null || operand == null) null else FilterValue(comparison, operand)
        }
        else -> null
    }
}

private fun parseReduceByKey(tokens: List<String>): Transform? {
    if (tokens.size != 3) return null
    val reducer = Reducer.entries.firstOrNull { it.token == tokens[1] } ?: return null
    return integerIn(tokens[2], 1L..PARTITION_LIMIT)?.let { ReduceByKey(reducer, it.toInt()) }
}

private fun parseJoin(tokens: List<String>): Transform? {
    if (tokens.size !in 3..4 || !isName(tokens[1])) return null
    if (tokens.size == 4 && tokens[3] != NULL_SAFE) return null
    val partitions = integerIn(tokens[2], 1L..PARTITION_LIMIT) ?: return null
    return Join(tokens[1], partitions.toInt(), nullSafe = tokens.size == 4)
}

private fun partitionsArgument(tokens: List<String>): Int? =
    tokens.takeIf { it.size == 2 }?.let { integerIn(it[1], 1L..PARTITION_LIMIT) }?.toInt()

private fun operandArgument(token: String): Long? = integerIn(token, -OPERAND_LIMIT..OPERAND_LIMIT)
