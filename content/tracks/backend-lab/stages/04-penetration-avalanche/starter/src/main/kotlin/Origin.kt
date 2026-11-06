import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

fun productBody(id: Int): String = "{\"id\":$id,\"price\":${1000 + id}}"

class OriginOverloaded : RuntimeException()

data class OriginSnapshot(
    val calls: Int,
    val missingCalls: Int,
    val peakSecond: Int,
    val peakActive: Int,
) {
    fun json(): String = "{\"calls\":$calls,\"missingCalls\":$missingCalls," +
        "\"peakSecond\":$peakSecond,\"peakActive\":$peakActive}"
}

class Origin {
    private val products = ConcurrentHashMap.newKeySet<Int>().apply { addAll(1..10_000) }
    private val monitor = Any()
    private val starts = ArrayDeque<Long>()
    private var calls = 0
    private var missingCalls = 0
    private var active = 0
    private var peakActive = 0
    private var peakSecond = 0

    fun create(id: Int) { products.add(id) }

    fun fetch(id: Int): String? {
        val overloaded = synchronized(monitor) {
            val now = System.nanoTime()
            while (starts.isNotEmpty() && now - starts.first >= 1_000_000_000L) starts.removeFirst()
            starts.addLast(now)
            calls++
            active++
            peakActive = maxOf(peakActive, active)
            peakSecond = maxOf(peakSecond, starts.size)
            starts.size > 300
        }
        try {
            if (overloaded) throw OriginOverloaded()
            Thread.sleep(80)
            if (id in products) return productBody(id)
            synchronized(monitor) { missingCalls++ }
            return null
        } finally {
            synchronized(monitor) { active-- }
        }
    }

    fun snapshot(): OriginSnapshot = synchronized(monitor) {
        OriginSnapshot(calls, missingCalls, peakSecond, peakActive)
    }
}
