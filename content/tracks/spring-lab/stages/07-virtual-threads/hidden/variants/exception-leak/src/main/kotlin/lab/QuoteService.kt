package lab

import java.util.concurrent.Semaphore

class QuoteService(private val resources: Resources, private val downstream: Downstream) {
    private val admission = Semaphore(4)

    fun quote(id: Int): Int? {
        if (!admission.tryAcquire()) return null
        val price = resources.price(id)
        val quote = downstream.quote(price)
        admission.release()
        return quote
    }
}
