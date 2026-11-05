import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AccountStore {
    private class Account(val id: String) {
        val lock = ReentrantLock()
        @Volatile var balance = 10_000
    }

    private val accounts = listOf("A", "B").associateWith { Account(it) }

    fun transfer(from: String, to: String, amount: Int, firstLockWindow: () -> Unit) {
        val source = accounts.getValue(from)
        val target = accounts.getValue(to)
        val first = if (source.id < target.id) source else target
        val second = if (source.id < target.id) target else source
        first.lock.withLock {
            firstLockWindow()
            second.lock.withLock {
                source.balance -= amount
                target.balance += amount
            }
        }
    }

    // Read only after the load finishes; volatile keeps this diagnostic independent of account locks.
    fun snapshot(): String = "A=${accounts.getValue("A").balance} B=${accounts.getValue("B").balance}"
}
