import java.util.TreeSet

class Timeline(private val limit: Int) {
    private val posts = TreeSet(newestFirst)

    fun include(post: Post) {
        posts.add(post)
        if (posts.size > limit) posts.pollLast()
    }

    fun snapshot(): List<Post> = posts.toList()
}

fun renderFeed(cost: Int, posts: List<Post>): String {
    val ids = if (posts.isEmpty()) "NONE" else posts.joinToString(",") { it.id.toString() }
    return "FEED $cost $ids"
}
