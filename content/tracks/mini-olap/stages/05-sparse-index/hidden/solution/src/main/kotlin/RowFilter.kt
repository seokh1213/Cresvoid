import kotlin.math.sign

class RowSet(private val words: LongArray) {
    infix fun and(other: RowSet): RowSet = RowSet(LongArray(words.size) { words[it] and other.words[it] })

    infix fun or(other: RowSet): RowSet = RowSet(LongArray(words.size) { words[it] or other.words[it] })

    fun rows(): IntArray {
        val rows = IntArray(words.sumOf { it.countOneBits() })
        var count = 0
        for (index in words.indices) {
            var remaining = words[index]
            while (remaining != 0L) {
                rows[count++] = (index shl 6) + remaining.countTrailingZeroBits()
                remaining = remaining and (remaining - 1)
            }
        }
        return rows
    }
}

class TruthVector(val trueRows: RowSet, val falseRows: RowSet) {
    operator fun not(): TruthVector = TruthVector(falseRows, trueRows)

    infix fun and(other: TruthVector): TruthVector =
        TruthVector(trueRows and other.trueRows, falseRows or other.falseRows)

    infix fun or(other: TruthVector): TruthVector =
        TruthVector(trueRows or other.trueRows, falseRows and other.falseRows)
}

fun Condition.evaluate(source: QuerySource): TruthVector = when (this) {
    is Condition.Comparison -> compareToValue(source.column(column), operator, value, source.rowCount)
    is Condition.IsNull -> nullCheck(source.column(column), source.rowCount)
    is Condition.IsNotNull -> !nullCheck(source.column(column), source.rowCount)
    is Condition.Not -> !operand.evaluate(source)
    is Condition.And -> left.evaluate(source) and right.evaluate(source)
    is Condition.Or -> left.evaluate(source) or right.evaluate(source)
}

private fun nullCheck(column: Column, rowCount: Int): TruthVector {
    val cells = column.cells
    return evaluateRows(rowCount, isUnknown = { false }, isTrue = { row -> cells[row] == column.nullCell })
}

private fun compareToValue(column: Column, operator: ComparisonOperator, value: Value?, rowCount: Int): TruthVector {
    if (value == null) return evaluateRows(rowCount, isUnknown = { true }, isTrue = { false })
    val cells = column.cells
    return when (column) {
        is IntColumn -> {
            val target = (value as IntValue).number
            val holdsForSign = BooleanArray(3) { index -> operator.holds(index - 1) }
            evaluateRows(rowCount, isUnknown = { row -> cells[row] == NULL_NUMBER }) { row ->
                holdsForSign[(cells[row] - target).sign + 1]
            }
        }
        is StringColumn -> {
            val matchesForSlot = column.slotsMatching(operator, (value as StringValue).text)
            evaluateRows(rowCount, isUnknown = { row -> cells[row] == NULL_CODE }) { row ->
                matchesForSlot[column.slotOf(cells[row])]
            }
        }
    }
}

// 알 수 없는 행에서도 isTrue 를 부르고 그 결과는 아는 행 표시로 지워 분기 없이 비트를 채운다
private inline fun evaluateRows(rowCount: Int, isUnknown: (row: Int) -> Boolean, isTrue: (row: Int) -> Boolean): TruthVector {
    val trueWords = LongArray((rowCount + 63) ushr 6)
    val falseWords = LongArray(trueWords.size)
    for (word in trueWords.indices) {
        val firstRow = word shl 6
        var knownBits = 0L
        var trueBits = 0L
        for (bit in 0 until minOf(64, rowCount - firstRow)) {
            val row = firstRow + bit
            val known = if (isUnknown(row)) 0L else 1L
            val holds = if (isTrue(row)) 1L else 0L
            knownBits = knownBits or (known shl bit)
            trueBits = trueBits or ((known and holds) shl bit)
        }
        trueWords[word] = trueBits
        falseWords[word] = knownBits and trueBits.inv()
    }
    return TruthVector(RowSet(trueWords), RowSet(falseWords))
}
