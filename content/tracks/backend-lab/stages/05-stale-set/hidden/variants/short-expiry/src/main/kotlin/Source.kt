import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class Product(val id: Int, val price: Long, val version: Long) {
    fun encode(): String = "$id,$price,$version"

    companion object {
        fun decode(value: String): Product {
            val fields = value.split(',')
            require(fields.size == 3)
            return Product(fields[0].toInt(), fields[1].toLong(), fields[2].toLong())
        }
    }
}

class Source {
    private val products = ConcurrentHashMap((1..12).associateWith { Product(it, 100, 0) })
    private val suspended = AtomicInteger()
    private val reads = AtomicInteger()
    @Volatile private var gate: CountDownLatch? = null

    fun current(id: Int): Product = products.getValue(id)

    fun read(id: Int): Product {
        reads.incrementAndGet()
        val snapshot = current(id)
        val barrier = gate
        if (barrier != null) {
            suspended.incrementAndGet()
            try {
                check(barrier.await(20, TimeUnit.SECONDS)) { "snapshot gate timed out" }
            } finally {
                suspended.decrementAndGet()
            }
        }
        Thread.sleep(80)
        return snapshot
    }

    fun update(id: Int, price: Long): Product =
        products.compute(id) { _, previous ->
            val product = requireNotNull(previous)
            product.copy(price = price, version = product.version + 1)
        } ?: error("unknown product")

    fun hold() {
        check(gate == null)
        gate = CountDownLatch(1)
    }

    fun release() {
        val previous = gate
        gate = null
        previous?.countDown()
    }

    fun pending(): Int = suspended.get()
    fun readCount(): Int = reads.get()
}
