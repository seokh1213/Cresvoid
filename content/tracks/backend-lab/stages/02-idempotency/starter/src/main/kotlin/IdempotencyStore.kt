class IdempotencyStore(private val gateway: PaymentGateway) {
    fun execute(key: String, request: PaymentRequest): PaymentResponse = gateway.charge(key, request)
}
