private const val ABSENT = -1

class Dictionary {
    private val codes = HashMap<String, Int>()
    private val texts = ArrayList<String>()
    private var order = ValueOrder(IntArray(0), IntArray(0))

    val size: Int get() = texts.size

    fun encode(text: String): Int = codes.getOrPut(text) {
        texts += text
        texts.size - 1
    }

    fun text(code: Int): String = texts[code]

    fun order(): ValueOrder {
        if (order.codeOfRank.size != texts.size) {
            val codeOfRank = texts.sorted().map(codes::getValue).toIntArray()
            val rankOfCode = IntArray(codeOfRank.size)
            codeOfRank.forEachIndexed { rank, code -> rankOfCode[code] = rank }
            order = ValueOrder(rankOfCode, codeOfRank)
        }
        return order
    }

    fun compare(left: Int, right: Int): Int {
        val rankOfCode = order().rankOfCode
        return rankOfCode[left].compareTo(rankOfCode[right])
    }

    fun matching(operator: ComparisonOperator, text: String): BooleanArray {
        val textCode = codes[text] ?: ABSENT
        if (operator == ComparisonOperator.EQUAL) return BooleanArray(texts.size) { code -> code == textCode }
        if (operator == ComparisonOperator.NOT_EQUAL) return BooleanArray(texts.size) { code -> code != textCode }
        val rankOfCode = order().rankOfCode
        val firstRankNotBefore = firstRankNotBefore(text)
        return BooleanArray(texts.size) { code ->
            val rank = rankOfCode[code]
            val sign = when {
                rank < firstRankNotBefore -> -1
                code == textCode -> 0
                else -> 1
            }
            operator.holds(sign)
        }
    }

    private fun firstRankNotBefore(text: String): Int {
        val codeOfRank = order().codeOfRank
        var low = 0
        var high = codeOfRank.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (texts[codeOfRank[middle]] < text) low = middle + 1 else high = middle
        }
        return low
    }
}

class ValueOrder(val rankOfCode: IntArray, val codeOfRank: IntArray)
