import java.util.PriorityQueue
import java.util.TreeSet

class Author {
    val posts = TreeSet(newestFirst)
    val followers = mutableSetOf<Int>()
    fun newest(): Post? = posts.firstOrNull()
}

class Reader {
    val copied = CopyFeed()
    val collectedAuthors = mutableSetOf<Int>()
}

class CopyFeed {
    private val heads = TreeSet(newestFirst)
    private var cached: Timeline? = null

    fun attach(head: Post?) {
        if (head != null) heads.add(head)
        cached = null
    }

    fun detach(head: Post?) {
        if (head != null) heads.remove(head)
        cached = null
    }

    fun published(post: Post, previousHead: Post?) {
        if (previousHead != null) heads.remove(previousHead)
        heads.add(if (previousHead == null || newestFirst.compare(post, previousHead) < 0) post else previousHead)
        cached?.include(post)
    }

    fun deleted(previousHead: Post?, currentHead: Post?) {
        if (previousHead != null) heads.remove(previousHead)
        if (currentHead != null) heads.add(currentHead)
        cached = null
    }

    fun snapshot(limit: Int, authors: Map<Int, Author>): List<Post> {
        val ready = cached
        if (ready != null) return ready.snapshot()
        // Any author beyond these heads already has L newer competitors.
        val streams = heads.take(limit).map { authors.getValue(it.author).posts.iterator() }
        val rebuilt = Timeline(limit)
        mergeNewest(streams, limit).forEach(rebuilt::include)
        cached = rebuilt
        return rebuilt.snapshot()
    }
}

private class PostCursor(val post: Post, val rest: Iterator<Post>)

fun mergeNewest(streams: List<Iterator<Post>>, limit: Int): List<Post> {
    val queue = PriorityQueue<PostCursor> { left, right -> newestFirst.compare(left.post, right.post) }
    for (stream in streams) {
        if (stream.hasNext()) queue.add(PostCursor(stream.next(), stream))
    }
    val result = ArrayList<Post>(limit)
    while (result.size < limit && queue.isNotEmpty()) {
        val cursor = queue.remove()
        result.add(cursor.post)
        if (cursor.rest.hasNext()) queue.add(PostCursor(cursor.rest.next(), cursor.rest))
    }
    return result
}
