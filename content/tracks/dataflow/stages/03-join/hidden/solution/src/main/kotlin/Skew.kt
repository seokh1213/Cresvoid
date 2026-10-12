class SkewSplit(val join: JoinNode, val left: List<List<IntRange>>, val right: List<List<IntRange>>) {
    val splitsAnyPartition: Boolean get() = left.any { it.size > 1 } || right.any { it.size > 1 }
}

fun skewPieces(shuffle: ShuffleOutput, settings: Settings): List<List<IntRange>> {
    val sizes = shuffle.sizes
    val baseline = maxOf(medianOf(sizes) * settings.skewFactor, settings.skewThreshold)
    val skewed = sizes.map { it > baseline }
    val calmSizes = sizes.filterIndexed { partition, _ -> !skewed[partition] }
    val targetSize = maxOf(settings.advisorySize, calmSizes.sum() / calmSizes.size)
    return sizes.indices.map { partition ->
        if (skewed[partition]) piecesOf(shuffle, partition, targetSize) else listOf(0 until shuffle.received[partition].size)
    }
}

private fun medianOf(sizes: LongArray): Long {
    val sorted = sizes.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

private fun piecesOf(shuffle: ShuffleOutput, partition: Int, targetSize: Long): List<IntRange> {
    val blockStarts = shuffle.blockStarts[partition]
    val blockBytes = shuffle.blockBytes[partition]
    val pieces = mutableListOf<IntRange>()
    var pieceStart = 0
    var pieceBytes = 0L
    for (task in blockBytes.indices) {
        val bytes = blockBytes[task]
        if (bytes > 0 && pieceBytes > 0 && pieceBytes + bytes > targetSize) {
            pieces += pieceStart until blockStarts[task]
            pieceStart = blockStarts[task]
            pieceBytes = 0
        }
        pieceBytes += bytes
    }
    pieces += pieceStart until blockStarts[blockBytes.size]
    return pieces
}
