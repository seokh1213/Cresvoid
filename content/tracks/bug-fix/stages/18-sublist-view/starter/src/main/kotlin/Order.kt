enum class OrderState {
    WAITING,
    DONE,
}

enum class AttemptOutcome {
    OK,
    RETRY,
}

class Order(val id: String, failures: Int) {
    var remainingFailures: Int = failures
        private set
    var attempts: Long = 0
        private set
    var state: OrderState = OrderState.WAITING
        private set

    fun attempt(): AttemptOutcome {
        check(state == OrderState.WAITING)
        attempts++
        if (remainingFailures > 0) {
            remainingFailures--
            return AttemptOutcome.RETRY
        }
        state = OrderState.DONE
        return AttemptOutcome.OK
    }

    fun describe(): String =
        "ORDER $id $state attempts=$attempts failures=$remainingFailures"
}

data class PageEntry(val orderId: String, val outcome: AttemptOutcome)

data class PageReceipt(val number: Int, val entries: List<PageEntry>) {
    fun render(): String = "PAGE $number " + entries.joinToString(",") {
        "${it.orderId}:${it.outcome}"
    }

    companion object {
        fun capture(
            number: Int,
            orderIds: List<String>,
            outcomes: List<AttemptOutcome>,
        ): PageReceipt {
            val entries = orderIds.mapIndexed { index, orderId ->
                PageEntry(orderId, outcomes[index])
            }
            return PageReceipt(number, entries)
        }
    }
}

data class BatchResult(val pages: List<PageReceipt>, val pending: Int) {
    fun summary(): String {
        val attempts = pages.sumOf { it.entries.size }
        val completed = pages.sumOf { page ->
            page.entries.count { it.outcome == AttemptOutcome.OK }
        }
        return "END pages=${pages.size} attempts=$attempts completed=$completed pending=$pending"
    }
}
