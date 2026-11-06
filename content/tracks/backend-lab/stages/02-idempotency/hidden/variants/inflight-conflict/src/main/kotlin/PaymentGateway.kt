import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class PaymentGateway {
    private val calls = ConcurrentHashMap<String, AtomicInteger>()
    private val sequence = AtomicInteger()
    private val heldKey = System.getenv("LAB_HOLD_KEY")
    private val release = CountDownLatch(1)
    @Volatile private var held = false

    fun isHeld(): Boolean = held
    fun releaseHeld() = release.countDown()

    fun callCount(key: String): Int = calls[key]?.get() ?: 0

    fun charge(key: String, request: PaymentRequest): PaymentResponse {
        calls.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        val attempt = sequence.incrementAndGet()
        if (key == heldKey) {
            held = true
            try {
                release.await()
            } finally {
                held = false
            }
        }
        Thread.sleep(100)
        // The provider accepted the charge before its response was lost.
        if (request.amount % 7 == 0) {
            return PaymentResponse(504, """{"error":"gateway_timeout","attemptId":"a-$attempt"}""")
        }
        return PaymentResponse(
            201,
            """{"paymentId":"p-$attempt","orderId":"${request.orderId}","amount":${request.amount}}""",
        )
    }
}
