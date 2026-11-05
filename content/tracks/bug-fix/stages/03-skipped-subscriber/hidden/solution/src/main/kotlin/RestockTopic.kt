data class RestockEvent(
    val product: String,
    val number: Int,
)

data class RestockDelivery(
    val event: RestockEvent,
    val recipients: List<String>,
)

class RestockTopic(val product: String) {
    private val subscribers = ArrayList<RestockSubscriber>()
    private var eventNumber = 0

    fun subscribe(user: String, mode: SubscriptionMode): Boolean {
        if (findSubscriber(user) != null) {
            return false
        }
        val subscriber = RestockSubscriber(
            user = user,
            mode = mode,
            firstEvent = eventNumber + 1,
            topic = this,
        )
        subscribers.add(subscriber)
        return true
    }

    fun unsubscribe(user: String): Boolean {
        val subscriber = findSubscriber(user) ?: return false
        return subscribers.remove(subscriber)
    }

    fun reserveInvitation(
        host: String,
        invitation: SubscriptionInvitation,
    ): Boolean {
        val subscriber = findSubscriber(host) ?: return false
        subscriber.reserveInvitation(invitation)
        return true
    }

    fun describeSubscribers(): String = subscribers.joinToString(",") {
        "${it.user}:${it.mode}"
    }

    fun notifyRestock(): RestockDelivery {
        eventNumber += 1
        val event = RestockEvent(
            product = product,
            number = eventNumber,
        )
        val recipients = mutableListOf<String>()
        val audience = subscribers.toList()
        var index = 0
        while (index < audience.size) {
            val subscriber = audience[index]
            if (subscriber.accepts(event)) {
                recipients.add(subscriber.user)
                subscriber.onRestock(event)
            }
            index += 1
        }
        return RestockDelivery(
            event = event,
            recipients = recipients.toList(),
        )
    }

    private fun findSubscriber(user: String): RestockSubscriber? =
        subscribers.firstOrNull { it.user == user }
}
