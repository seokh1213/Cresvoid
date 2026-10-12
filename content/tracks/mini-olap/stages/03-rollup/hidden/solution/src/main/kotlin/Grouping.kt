private const val NO_ROW = -1

class Groups(val idOfRow: IntArray, val firstRows: IntArray) {
    val count: Int get() = firstRows.size
}

fun groupRows(rows: IntArray, columns: List<Column>): Groups {
    val ids = IntArray(rows.size)
    var groupCount = 1
    // 열을 하나 더할 때마다 번호를 다시 매겨 키가 (그룹 수 × 칸 수) 를 넘지 않게 한다
    for (column in columns) {
        val slots = column.groupSlots(rows)
        val slotCount = column.groupSlotCount()
        val index = KeyIndex.forKeySpace(groupCount * slotCount, rows.size)
        for (position in rows.indices) ids[position] = index.idOf(ids[position] * slotCount + slots[position])
        groupCount = index.size
    }
    return Groups(ids, firstRowOfEachGroup(rows, ids, groupCount))
}

private fun firstRowOfEachGroup(rows: IntArray, ids: IntArray, groupCount: Int): IntArray {
    val firstRows = IntArray(groupCount) { NO_ROW }
    for (position in rows.indices) {
        if (firstRows[ids[position]] == NO_ROW) firstRows[ids[position]] = rows[position]
    }
    return firstRows
}
