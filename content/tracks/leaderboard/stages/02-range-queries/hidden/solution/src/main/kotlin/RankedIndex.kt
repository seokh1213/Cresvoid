data class Participant(val user: Int, val score: Long, val arrival: Long) : Comparable<Participant> {
    override fun compareTo(other: Participant): Int {
        val byScore = other.score.compareTo(score)
        return if (byScore != 0) byScore else arrival.compareTo(other.arrival)
    }
}

class RankedIndex {
    private class Node(var participant: Participant) {
        var left: Node? = null
        var right: Node? = null
        var height = 1
        var size = 1
    }

    private var root: Node? = null
    val size: Int get() = size(root)

    fun insert(participant: Participant) {
        root = insert(root, participant)
    }

    fun remove(participant: Participant) {
        root = remove(root, participant)
    }

    fun clear() {
        root = null
    }

    fun countBefore(participant: Participant): Int {
        var count = 0
        var node = root
        while (node != null) {
            if (participant <= node.participant) {
                node = node.left
            } else {
                count += size(node.left) + 1
                node = node.right
            }
        }
        return count
    }

    fun range(start: Int, endExclusive: Int): List<Participant> {
        val participants = mutableListOf<Participant>()
        collect(root, start, endExclusive, participants)
        return participants
    }

    private fun collect(node: Node?, start: Int, end: Int, result: MutableList<Participant>) {
        if (node == null || start >= end || end <= 0 || start >= node.size) return
        val position = size(node.left)
        collect(node.left, start, minOf(end, position), result)
        if (position >= start && position < end) result.add(node.participant)
        collect(node.right, start - position - 1, end - position - 1, result)
    }

    private fun insert(node: Node?, participant: Participant): Node {
        if (node == null) return Node(participant)
        if (participant < node.participant) {
            node.left = insert(node.left, participant)
        } else {
            node.right = insert(node.right, participant)
        }
        return balance(node)
    }

    private fun remove(node: Node?, participant: Participant): Node? {
        node ?: return null
        when {
            participant < node.participant -> node.left = remove(node.left, participant)
            participant > node.participant -> node.right = remove(node.right, participant)
            else -> {
                if (node.left == null) return node.right
                if (node.right == null) return node.left
                var successor = checkNotNull(node.right)
                while (successor.left != null) successor = checkNotNull(successor.left)
                node.participant = successor.participant
                node.right = remove(node.right, successor.participant)
            }
        }
        return balance(node)
    }

    private fun balance(node: Node): Node {
        refresh(node)
        val tilt = height(node.left) - height(node.right)
        if (tilt > 1) {
            val left = checkNotNull(node.left)
            if (height(left.left) < height(left.right)) node.left = rotateLeft(left)
            return rotateRight(node)
        }
        if (tilt < -1) {
            val right = checkNotNull(node.right)
            if (height(right.right) < height(right.left)) node.right = rotateRight(right)
            return rotateLeft(node)
        }
        return node
    }

    private fun rotateLeft(node: Node): Node {
        val pivot = checkNotNull(node.right)
        node.right = pivot.left
        pivot.left = node
        refresh(node)
        refresh(pivot)
        return pivot
    }

    private fun rotateRight(node: Node): Node {
        val pivot = checkNotNull(node.left)
        node.left = pivot.right
        pivot.right = node
        refresh(node)
        refresh(pivot)
        return pivot
    }

    private fun refresh(node: Node) {
        node.height = maxOf(height(node.left), height(node.right)) + 1
        node.size = size(node.left) + size(node.right) + 1
    }

    private fun height(node: Node?): Int = node?.height ?: 0
    private fun size(node: Node?): Int = node?.size ?: 0
}
