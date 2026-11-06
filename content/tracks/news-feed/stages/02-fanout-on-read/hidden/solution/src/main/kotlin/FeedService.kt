import java.util.TreeSet

class FeedService(private val limit: Int, private val mode: Mode) {
    private val usedIds = mutableSetOf<Int>()
    private val originals = mutableMapOf<Int, TreeSet<Post>>()
    private val followers = mutableMapOf<Int, MutableSet<Int>>()
    private val activeAuthors = mutableMapOf<Int, MutableSet<Int>>()
    private val feeds = mutableMapOf<Int, Timeline>()

    fun execute(command: Command): String = when (command) {
        is Command.Follow -> follow(command)
        is Command.Publish -> publish(command)
        is Command.Feed -> feed(command.reader)
    }

    private fun follow(command: Command.Follow): String {
        val (reader, author) = command
        if (reader == author) return "ERR SELF_FOLLOW"
        if (!followers.getOrPut(author) { mutableSetOf() }.add(reader)) return "UNCHANGED"
        if (!originals[author].isNullOrEmpty()) {
            activeAuthors.getOrPut(reader) { mutableSetOf() }.add(author)
        }
        if (mode == Mode.Write) {
            val timeline = feeds.getOrPut(reader) { Timeline(limit) }
            originals[author]?.take(limit)?.forEach(timeline::include)
        }
        return "FOLLOWED"
    }

    private fun publish(command: Command.Publish): String {
        val (id, author, timestamp) = command
        if (!usedIds.add(id)) return "ERR POST_EXISTS"
        val post = Post(id, author, timestamp)
        val posts = originals.getOrPut(author) { TreeSet(newestFirst) }
        val firstPost = posts.isEmpty()
        posts.add(post)
        if (firstPost) {
            for (reader in followers[author].orEmpty()) {
                activeAuthors.getOrPut(reader) { mutableSetOf() }.add(author)
            }
        }
        return when (mode) {
            Mode.Read -> "POSTED 0"
            Mode.Write -> {
                val recipients = followers[author].orEmpty()
                for (reader in recipients) feeds.getValue(reader).include(post)
                "POSTED ${recipients.size}"
            }
        }
    }

    private fun feed(reader: Int): String = when (mode) {
        Mode.Write -> {
            val posts = feeds[reader]?.snapshot().orEmpty()
            renderFeed(posts.size, posts)
        }
        Mode.Read -> collectFeed(reader)
    }

    private fun collectFeed(reader: Int): String {
        val result = Timeline(limit)
        var cost = 0
        for (author in activeAuthors[reader].orEmpty()) {
            val candidates = originals[author]?.take(limit).orEmpty()
            cost += candidates.size
            candidates.forEach(result::include)
        }
        return renderFeed(cost, result.snapshot())
    }
}
