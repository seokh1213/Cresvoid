import java.util.TreeSet

class FeedService(private val limit: Int) {
    private val usedIds = mutableSetOf<Int>()
    private val originals = mutableMapOf<Int, TreeSet<Post>>()
    private val followers = mutableMapOf<Int, MutableSet<Int>>()
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
        val timeline = feeds.getOrPut(reader) { Timeline(limit) }
        originals[author]?.take(limit)?.forEach(timeline::include)
        return "FOLLOWED"
    }

    private fun publish(command: Command.Publish): String {
        val (id, author, timestamp) = command
        if (!usedIds.add(id)) return "ERR POST_EXISTS"
        val post = Post(id, author, timestamp)
        originals.getOrPut(author) { TreeSet(newestFirst) }.add(post)
        val recipients = followers[author].orEmpty()
        for (reader in recipients) feeds.getValue(reader).include(post)
        return "POSTED ${recipients.size}"
    }

    private fun feed(reader: Int): String {
        val posts = feeds[reader]?.snapshot().orEmpty()
        return renderFeed(posts.size, posts)
    }
}
