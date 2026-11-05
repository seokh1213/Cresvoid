import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

interface UsageClock {
    fun now(): Instant
}

class CommandClock : UsageClock {
    private var current: Instant? = null

    fun advanceTo(occurredAt: Instant) {
        val previous = current
        require(previous == null || !occurredAt.isBefore(previous))
        current = occurredAt
    }

    override fun now(): Instant = checkNotNull(current)
}

class UsageCalendar {
    private val accountingOffset = ZoneOffset.UTC

    fun dateOf(instant: Instant): LocalDate =
        instant.atOffset(accountingOffset).toLocalDate()
}

class AccountingDay(
    private val clock: UsageClock,
    private val calendar: UsageCalendar,
) {
    fun current(): LocalDate = calendar.dateOf(clock.now())
}
