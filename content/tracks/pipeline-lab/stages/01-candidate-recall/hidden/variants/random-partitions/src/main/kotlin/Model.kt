data class TokenRecord(val id: Int, val category: String, val timestamp: Long, val tokens: List<Int>)
data class Threshold(val numerator: Long, val denominator: Long)
data class Batch(val threshold: Threshold, val records: List<TokenRecord>)
data class ExactKey(val category: String, val tokens: List<Int>)
data class ExactGroup(val key: ExactKey, val ids: List<Int>)
data class GroupPair(val left: Int, val right: Int)
data class IdPair(val left: Int, val right: Int) : Comparable<IdPair> {
    override fun compareTo(other: IdPair): Int = compareValuesBy(this, other, IdPair::left, IdPair::right)
}
data class MatchResult(val pairs: List<IdPair>, val comparisons: Long)

fun parseBatch(text: String): Batch {
    val allLines = text.lines()
    val lines = if (allLines.size > 1 && allLines.last().isEmpty()) allLines.dropLast(1) else allLines
    val header = lines.first().split(' ')
    require(header.size == 3)
    val count = header[0].toInt()
    val threshold = Threshold(header[1].toLong(), header[2].toLong())
    require(count in 0..500 && lines.size == count + 1)
    require(threshold.numerator in 1..threshold.denominator && threshold.denominator <= 1_000_000)
    val records = lines.drop(1).map { line ->
        val fields = line.split(' ')
        require(fields.size == 4)
        val tokens = if (fields[3] == "-") emptyList() else fields[3].split(',').map(String::toInt)
        require(tokens.size <= 32 && tokens.all { it >= 0 } && tokens == tokens.distinct().sorted())
        require(fields[1].matches(Regex("[a-z][a-z0-9-]{0,31}")))
        TokenRecord(fields[0].toInt(), fields[1], fields[2].toLong(), tokens).also { require(it.id > 0) }
    }
    require(records.map { it.id }.distinct().size == records.size)
    return Batch(threshold, records)
}
