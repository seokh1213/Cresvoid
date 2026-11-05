enum class SubscriptionMode {
    ONCE,
    KEEP,
}

data class SubscriptionInvitation(
    val user: String,
    val mode: SubscriptionMode,
)

interface RestockListener {
    fun accepts(event: RestockEvent): Boolean

    fun onRestock(event: RestockEvent)
}

class RestockSubscriber(
    val user: String,
    val mode: SubscriptionMode,
    private val firstEvent: Int,
    private val topic: RestockTopic,
) : RestockListener {
    private var invitation: SubscriptionInvitation? = null

    fun reserveInvitation(reservation: SubscriptionInvitation) {
        invitation = reservation
    }

    override fun accepts(event: RestockEvent): Boolean {
        return event.product == topic.product && event.number >= firstEvent
    }

    override fun onRestock(event: RestockEvent) {
        check(accepts(event))
        val reservation = invitation
        invitation = null
        if (mode == SubscriptionMode.ONCE) {
            topic.unsubscribe(user)
        }
        if (reservation != null) {
            topic.subscribe(reservation.user, reservation.mode)
        }
    }
}
