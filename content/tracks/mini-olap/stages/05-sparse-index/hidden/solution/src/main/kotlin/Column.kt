// INT 값은 ±1,000,000,000 안에 있어 Int.MIN_VALUE 를 NULL 표시로 쓸 수 있다
const val NULL_NUMBER = Int.MIN_VALUE
const val NULL_CODE = -1
const val NOT_A_CELL = Long.MIN_VALUE
const val NULL_SLOT = 0

sealed class Column {
    var cells = IntArray(16)
        private set
    var size = 0
        private set

    abstract val nullCell: Int

    fun append(cell: Int) {
        if (size == cells.size) cells = cells.copyOf(size + size / 2)
        cells[size++] = cell
        remember(cell)
    }

    operator fun set(row: Int, cell: Int) {
        cells[row] = cell
        remember(cell)
    }

    fun truncate(newSize: Int) {
        size = newSize
    }

    protected open fun remember(cell: Int) {}

    fun parseCell(line: String, start: Int, end: Int): Long {
        val isNullMarker = end - start == 2 && line[start] == '\\' && line[start + 1] == 'N'
        return if (isNullMarker) nullCell.toLong() else parseValueCell(line, start, end)
    }

    abstract fun encode(value: Value?): Int

    abstract fun emptyCopy(): Column

    abstract fun compareCells(left: Int, right: Int): Int

    protected abstract fun parseValueCell(line: String, start: Int, end: Int): Long

    abstract fun groupSlotCount(): Long

    abstract fun groupSlots(rows: IntArray): IntArray

    abstract fun resultOf(cells: IntArray): ResultColumn
}

class IntColumn : Column() {
    private var smallest = Int.MAX_VALUE
    private var largest = Int.MIN_VALUE

    override val nullCell: Int = NULL_NUMBER

    override fun remember(cell: Int) {
        if (cell == NULL_NUMBER) return
        if (cell < smallest) smallest = cell
        if (cell > largest) largest = cell
    }

    override fun encode(value: Value?): Int = if (value is IntValue) value.number.toInt() else NULL_NUMBER

    override fun emptyCopy(): Column = IntColumn()

    override fun compareCells(left: Int, right: Int): Int = left.compareTo(right)

    override fun parseValueCell(line: String, start: Int, end: Int): Long {
        val number = parseInt(line, start, end)
        return if (number == NOT_AN_INT) NOT_A_CELL else number
    }

    override fun groupSlotCount(): Long = if (smallest > largest) 1 else largest.toLong() - smallest + 2

    override fun groupSlots(rows: IntArray): IntArray {
        val cells = cells
        val offset = smallest - 1
        return IntArray(rows.size) { index ->
            val cell = cells[rows[index]]
            if (cell == NULL_NUMBER) NULL_SLOT else cell - offset
        }
    }

    override fun resultOf(cells: IntArray): ResultColumn =
        NumberResult(LongArray(cells.size) { cells[it].toLong() }, BooleanArray(cells.size) { cells[it] == NULL_NUMBER })
}

class StringColumn(val dictionary: Dictionary = Dictionary()) : Column() {
    override val nullCell: Int = NULL_CODE

    override fun encode(value: Value?): Int = if (value is StringValue) dictionary.encode(value.text) else NULL_CODE

    override fun emptyCopy(): Column = StringColumn(dictionary)

    // 행을 넣을 때마다 쓰이므로 사전이 자라도 순서를 다시 매기지 않게 글자를 바로 비교한다
    override fun compareCells(left: Int, right: Int): Int = dictionary.compareTexts(left, right)

    override fun parseValueCell(line: String, start: Int, end: Int): Long {
        for (position in start until end) {
            if (!isStringCharacter(line[position])) return NOT_A_CELL
        }
        return dictionary.encode(line.substring(start, end)).toLong()
    }

    fun slotsMatching(operator: ComparisonOperator, text: String): BooleanArray {
        val matches = dictionary.matching(operator, text)
        return BooleanArray(matches.size + 1) { slot -> slot != NULL_SLOT && matches[slot - 1] }
    }

    fun slotOf(code: Int): Int = code - NULL_CODE + NULL_SLOT

    override fun groupSlotCount(): Long = dictionary.size + 1L

    override fun groupSlots(rows: IntArray): IntArray {
        val cells = cells
        return IntArray(rows.size) { index -> slotOf(cells[rows[index]]) }
    }

    override fun resultOf(cells: IntArray): ResultColumn = TextResult(cells, dictionary)
}
