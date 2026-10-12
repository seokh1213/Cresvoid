fun joinGroups(left: Partition, right: Partition, nullSafe: Boolean, dictionary: KeyDictionary): Partition {
    val ranks = dictionary.ranks()
    val leftLists = left.values as Lists
    val rightLists = right.values as Lists
    val matchedLeft = IntArray(minOf(left.size, right.size))
    val matchedRight = IntArray(matchedLeft.size)
    var matches = 0
    var outputSize = 0
    var leftGroup = 0
    var rightGroup = 0
    while (leftGroup < left.size && rightGroup < right.size) {
        val order = ranks[left.keys[leftGroup]].compareTo(ranks[right.keys[rightGroup]])
        if (order == 0 && (left.keys[leftGroup] != NULL_KEY || nullSafe)) {
            matchedLeft[matches] = leftGroup
            matchedRight[matches] = rightGroup
            matches++
            outputSize += leftLists.length(leftGroup) * rightLists.length(rightGroup)
        }
        if (order <= 0) leftGroup++
        if (order >= 0) rightGroup++
    }

    val keys = IntArray(outputSize)
    val leftValues = LongArray(outputSize)
    val rightValues = LongArray(outputSize)
    var row = 0
    for (match in 0 until matches) {
        val key = left.keys[matchedLeft[match]]
        for (leftIndex in leftLists.starts[matchedLeft[match]] until leftLists.starts[matchedLeft[match] + 1]) {
            for (rightIndex in rightLists.starts[matchedRight[match]] until rightLists.starts[matchedRight[match] + 1]) {
                keys[row] = key
                leftValues[row] = leftLists.elements[leftIndex]
                rightValues[row] = rightLists.elements[rightIndex]
                row++
            }
        }
    }
    return Partition(outputSize, keys, Pairs(leftValues, rightValues))
}
