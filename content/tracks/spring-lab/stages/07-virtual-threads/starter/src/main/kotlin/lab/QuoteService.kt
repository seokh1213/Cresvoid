package lab

class QuoteService(private val resources: Resources, private val downstream: Downstream) {
    fun quote(id: Int): Int? {
        val price = resources.price(id)
        return downstream.quote(price)
    }
}
