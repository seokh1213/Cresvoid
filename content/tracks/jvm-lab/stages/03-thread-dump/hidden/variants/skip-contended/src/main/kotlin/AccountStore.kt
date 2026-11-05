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
        source.lock.withLock {
            firstLockWindow()
            if (!target.lock.tryLock()) return
            try {
                source.balance -= amount
                target.balance += amount
            } finally {
                target.lock.unlock()
            }
        }
    }

    // Read only after the load finishes; volatile keeps this diagnostic independent of account locks.
    fun snapshot(): String = "A=${accounts.getValue("A").balance} B=${accounts.getValue("B").balance}"
}
