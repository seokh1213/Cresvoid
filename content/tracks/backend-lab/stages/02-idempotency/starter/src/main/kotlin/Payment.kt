data class PaymentRequest(val orderId: String, val amount: Int)
data class PaymentResponse(val status: Int, val body: String)

fun keyConflict() = PaymentResponse(422, """{"error":"key_conflict"}""")
