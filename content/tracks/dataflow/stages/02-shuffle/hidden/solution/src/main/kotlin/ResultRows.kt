fun resultLines(partitions: List<Partition>, limit: Long?, dictionary: KeyDictionary): List<String> {
    val rows = Partition.concat(partitions)
    val wanted = minOf(limit ?: Long.MAX_VALUE, rows.size.toLong()).toInt()
    if (wanted == 0) return emptyList()
    val ranks = dictionary.ranks()
    val rowOrder = compareBy<Int> { ranks[rows.keys[it]] }.thenComparator { row, otherRow -> rows.values.compare(row, otherRow) }
    return leadingRows(rows, wanted, ranks)
        .sortedWith(rowOrder)
        .take(wanted)
        .map { "${dictionary.text(rows.keys[it])} ${rows.values.format(it)}" }
}

private fun leadingRows(rows: Partition, wanted: Int, ranks: IntArray): List<Int> {
    val countByRank = IntArray(ranks.size)
    for (row in 0 until rows.size) countByRank[ranks[rows.keys[row]]]++
    var lastRank = 0
    var covered = countByRank[0]
    while (covered < wanted) {
        lastRank++
        covered += countByRank[lastRank]
    }
    return (0 until rows.size).filter { ranks[rows.keys[it]] <= lastRank }
}
