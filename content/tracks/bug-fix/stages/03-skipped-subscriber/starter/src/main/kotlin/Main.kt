fun main() {
    val console = AlertConsole()
    System.`in`.bufferedReader().useLines { lines ->
        for (line in lines) {
            if (line.isNotBlank()) {
                val words = line.trim().split(Regex("\\s+"))
                println(console.execute(words))
            }
        }
    }
}

private class AlertConsole {
    private val topics = mutableMapOf<String, RestockTopic>()

    fun execute(words: List<String>): String {
        val command = words[0]
        val product = words[1]
        val topic = topics.getOrPut(product) { RestockTopic(product) }
        return when (command) {
            "WATCH" -> watch(topic, words)
            "UNWATCH" -> unwatch(topic, words[2])
            "INVITE" -> invite(topic, words)
            "LIST" -> list(topic)
            "RESTOCK" -> restock(topic)
            else -> error("Unknown command: $command")
        }
    }

    private fun watch(topic: RestockTopic, words: List<String>): String {
        val user = words[2]
        val mode = SubscriptionMode.valueOf(words[3])
        val status = if (topic.subscribe(user, mode)) "WATCHED" else "EXISTS"
        return "$status ${topic.product} $user"
    }

    private fun unwatch(topic: RestockTopic, user: String): String {
        val status = if (topic.unsubscribe(user)) "UNWATCHED" else "ABSENT"
        return "$status ${topic.product} $user"
    }

    private fun invite(topic: RestockTopic, words: List<String>): String {
        val host = words[2]
        val invitation = SubscriptionInvitation(
            user = words[3],
            mode = SubscriptionMode.valueOf(words[4]),
        )
        if (!topic.reserveInvitation(host, invitation)) {
            return "ABSENT ${topic.product} $host"
        }
        return "INVITED ${topic.product} $host ${invitation.user}"
    }

    private fun list(topic: RestockTopic): String {
        val subscribers = topic.describeSubscribers().ifEmpty { "-" }
        return "SUBSCRIBERS ${topic.product} $subscribers"
    }

    private fun restock(topic: RestockTopic): String {
        val delivery = topic.notifyRestock()
        val recipients = delivery.recipients.joinToString(",").ifEmpty { "-" }
        return "NOTIFIED ${topic.product} ${delivery.event.number} $recipients"
    }
}
