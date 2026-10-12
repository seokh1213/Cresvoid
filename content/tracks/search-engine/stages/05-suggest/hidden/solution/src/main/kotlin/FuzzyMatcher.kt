private const val MAX_EDIT_DISTANCE = 2

private val BY_DISTANCE_THEN_FREQUENCY_THEN_TOKEN =
    compareBy<FuzzyMatch> { it.distance }
        .thenByDescending { it.documentFrequency }
        .thenBy { it.token }

data class FuzzyMatch(val token: String, val distance: Int, val documentFrequency: Int)

class FuzzyMatcher(private val vocabulary: TokenTrie) {

    fun closestTokens(queryToken: String, limit: Int): List<FuzzyMatch> =
        EditDistanceWalk(queryToken).matchesBelow(vocabulary.root)
            .sortedWith(BY_DISTANCE_THEN_FREQUENCY_THEN_TOKEN)
            .take(limit)
}

private class EditDistanceWalk(private val queryToken: String) {
    // 깊이 d 인 행의 칸은 모두 d − 질의 길이 이상이라 질의 길이 + 상한 + 1 깊이까지만 행을 채운다
    private val rowsByDepth = Array(queryToken.length + MAX_EDIT_DISTANCE + 2) { IntArray(queryToken.length + 1) }
    private val matches = mutableListOf<FuzzyMatch>()

    fun matchesBelow(root: TrieNode): List<FuzzyMatch> {
        val emptyPrefixRow = rowsByDepth[0]
        for (column in emptyPrefixRow.indices) emptyPrefixRow[column] = column
        for (child in root.children) {
            if (isWithinReach(child.letter)) visit(child, depth = 1)
        }
        return matches
    }

    // 부류가 다른 두 토큰의 거리는 두 길이 가운데 큰 값이라 질의가 상한보다 길면 다른 부류는 볼 필요가 없다
    private fun isWithinReach(firstLetter: Char): Boolean =
        queryToken.length <= MAX_EDIT_DISTANCE || isHangulSyllable(firstLetter) == isHangulSyllable(queryToken.first())

    private fun visit(node: TrieNode, depth: Int) {
        val rowMinimum = fillRowAndFindMinimum(depth, node.letter)
        val distance = rowsByDepth[depth][queryToken.length]
        val token = node.token
        if (token != null && distance <= MAX_EDIT_DISTANCE) {
            matches += FuzzyMatch(token, distance, node.documentFrequency)
        }
        if (rowMinimum > MAX_EDIT_DISTANCE) return
        for (child in node.children) visit(child, depth + 1)
    }

    private fun fillRowAndFindMinimum(depth: Int, letter: Char): Int {
        val previous = rowsByDepth[depth - 1]
        val row = rowsByDepth[depth]
        row[0] = depth
        var rowMinimum = depth
        for (column in 1 until row.size) {
            val substitutionCost = if (queryToken[column - 1] == letter) 0 else 1
            row[column] = minOf(
                previous[column] + 1,
                row[column - 1] + 1,
                previous[column - 1] + substitutionCost,
            )
            rowMinimum = minOf(rowMinimum, row[column])
        }
        return rowMinimum
    }
}
