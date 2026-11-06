class FeedService(private val limit: Int, private val mode: Mode) {
    private val authors = mutableMapOf<Int, Author>()
    private val readers = mutableMapOf<Int, Reader>()
    private val published = mutableMapOf<Int, Post>()

    fun execute(command: Command): String = when (command) {
        is Command.Follow -> follow(command)
        is Command.Unfollow -> unfollow(command)
        is Command.Publish -> publish(command)
        is Command.Delete -> delete(command.id)
        is Command.Feed -> feed(command.reader)
    }

    private fun copies(author: Author): Boolean = when (val policy = mode) {
        Mode.Write -> true
        Mode.Read -> false
        is Mode.Hybrid -> author.followers.size <= policy.threshold
    }

    private fun follow(command: Command.Follow): String {
        val (readerId, authorId) = command
        if (readerId == authorId) return "ERR SELF_FOLLOW"
        val author = authors.getOrPut(authorId) { Author() }
        val copiedBefore = copies(author)
        if (!author.followers.add(readerId)) return "UNCHANGED"
        readers.getOrPut(readerId) { Reader() }
        if (copiedBefore != copies(author)) reclassify(authorId, author)
        else attach(readerId, authorId, author)
        return "FOLLOWED"
    }

    private fun unfollow(command: Command.Unfollow): String {
        val (readerId, authorId) = command
        val author = authors[authorId] ?: return "UNCHANGED"
        val copiedBefore = copies(author)
        if (!author.followers.remove(readerId)) return "UNCHANGED"
        detach(readerId, authorId, author)
        if (copiedBefore != copies(author)) reclassify(authorId, author)
        return "UNFOLLOWED"
    }

    private fun attach(readerId: Int, authorId: Int, author: Author) {
        val reader = readers.getValue(readerId)
        if (copies(author)) reader.copied.attach(author.newest())
        else if (author.posts.isNotEmpty()) reader.collectedAuthors.add(authorId)
    }

    private fun detach(readerId: Int, authorId: Int, author: Author) {
        val reader = readers.getValue(readerId)
        reader.copied.detach(author.newest())
        reader.collectedAuthors.remove(authorId)
    }

    private fun reclassify(authorId: Int, author: Author) {
        for (readerId in author.followers) {
            detach(readerId, authorId, author)
            attach(readerId, authorId, author)
        }
    }

    private fun publish(command: Command.Publish): String {
        val (id, authorId, timestamp) = command
        if (id in published) return "ERR POST_EXISTS"
        val post = Post(id, authorId, timestamp)
        val author = authors.getOrPut(authorId) { Author() }
        val previousHead = author.newest()
        published[id] = post
        author.posts.add(post)
        if (!copies(author)) {
            if (previousHead == null) {
                for (readerId in author.followers) readers.getValue(readerId).collectedAuthors.add(authorId)
            }
            return "POSTED 0"
        }
        for (readerId in author.followers) readers.getValue(readerId).copied.published(post, previousHead)
        return "POSTED ${author.followers.size}"
    }

    private fun delete(id: Int): String {
        val post = published[id] ?: return "ERR NO_POST"
        val author = authors.getValue(post.author)
        val previousHead = author.newest()
        if (!author.posts.remove(post)) return "UNCHANGED"
        if (copies(author)) {
            val currentHead = author.newest()
            for (readerId in author.followers) readers.getValue(readerId).copied.deleted(previousHead, currentHead)
        } else if (author.posts.isEmpty()) {
            for (readerId in author.followers) readers.getValue(readerId).collectedAuthors.remove(post.author)
        }
        return "DELETED"
    }

    private fun feed(readerId: Int): String {
        val reader = readers[readerId] ?: return renderFeed(0, emptyList())
        val copied = reader.copied.snapshot(limit, authors)
        val streams = mutableListOf(copied.iterator())
        var cost = copied.size
        for (authorId in reader.collectedAuthors) {
            val posts = authors.getValue(authorId).posts
            cost += minOf(limit, posts.size)
            streams.add(posts.iterator())
        }
        return renderFeed(cost, mergeNewest(streams, limit))
    }
}
