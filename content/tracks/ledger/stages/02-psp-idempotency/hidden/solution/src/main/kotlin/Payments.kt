class Payments(private val ledger: Ledger) {
    private val requests = mutableMapOf<String, PaymentRequest>()
    private val refundUsage = mutableMapOf<String, Long>()
    private val observations = mutableMapOf<String, Observation>()
    private val remote = RemoteGateway()

    fun successful(): Map<String, RequestDetails> = requests.filterValues { it.state == RequestState.SUCCEEDED }
        .mapValues { it.value.details }

    fun accept(key: String, details: RequestDetails): String {
        val existing = requests[key]
        if (existing != null) {
            return if (existing.details == details) state(key, existing) else "ERR KEY_CONFLICT"
        }
        val error = when (details) {
            is RequestDetails.Pay -> validatePay(details)
            is RequestDetails.Refund -> validateRefund(details)
        }
        if (error != null) return "ERR $error"
        val request = PaymentRequest(details)
        requests[key] = request
        if (details is RequestDetails.Refund) {
            refundUsage[details.original] = refundUsage.getOrDefault(details.original, 0L) + details.amount
        }
        return state(key, request)
    }

    private fun validatePay(pay: RequestDetails.Pay): String? {
        if (pay.amount <= 0) return "AMOUNT"
        val debit = ledger.account(pay.debit) ?: return "NOT_FOUND"
        val credit = ledger.account(pay.credit) ?: return "NOT_FOUND"
        return when {
            pay.debit == pay.credit -> "SAME_ACCOUNT"
            debit.terms.currency != pay.currency || credit.terms.currency != pay.currency -> "CURRENCY"
            debit.terms.policy != BalancePolicy.ANY || credit.terms.policy != BalancePolicy.ANY -> "ACCOUNT_POLICY"
            else -> null
        }
    }

    private fun validateRefund(refund: RequestDetails.Refund): String? {
        if (refund.amount <= 0) return "AMOUNT"
        val original = requests[refund.original] ?: return "NOT_FOUND"
        val pay = original.details as? RequestDetails.Pay ?: return "PAYMENT_STATE"
        return when {
            original.state != RequestState.SUCCEEDED -> "PAYMENT_STATE"
            pay.currency != refund.currency -> "CURRENCY"
            refund.amount > pay.amount - refundUsage.getOrDefault(refund.original, 0L) -> "REFUND_LIMIT"
            else -> null
        }
    }

    fun status(key: String): String {
        val request = requests[key] ?: return "ERR NOT_FOUND"
        val details = request.details
        val journal = if (request.state == RequestState.SUCCEEDED) "@$key" else "-"
        val original = (details as? RequestDetails.Refund)?.original ?: "-"
        return "STATUS $key ${details.kind} ${request.state} ${details.currency} ${details.amount} $journal $original"
    }

    fun call(key: String): String {
        val request = requests[key] ?: return "ERR NOT_FOUND"
        val error = observationError(key, request)
        if (error != null) return "ERR $error"
        if (request.state == RequestState.UNKNOWN) return "ERR STATE"
        val observation = remote.call(key) ?: return "ERR NO_SCRIPT"
        return observe(key, observation)
    }

    fun query(key: String): String {
        val request = requests[key] ?: return "ERR NOT_FOUND"
        val error = observationError(key, request)
        if (error != null) return "ERR $error"
        return observe(key, remote.query(key))
    }

    private fun observationError(key: String, request: PaymentRequest): String? = when {
        key in observations -> "OBSERVATION_PENDING"
        request.state == RequestState.SUCCEEDED || request.state == RequestState.FAILED -> "STATE"
        else -> null
    }

    private fun observe(key: String, observation: Observation): String {
        observations[key] = observation
        return "OBSERVED $key $observation"
    }

    fun commit(key: String): String {
        val request = requests[key] ?: return "ERR NOT_FOUND"
        val observation = observations[key] ?: return "ERR NO_OBSERVATION"
        if (observation == Observation.OK) postSuccess(key, request.details)
        request.commit(observation)
        val refund = request.details as? RequestDetails.Refund
        if (refund != null && request.state == RequestState.FAILED) {
            refundUsage[refund.original] = refundUsage.getValue(refund.original) - refund.amount
        }
        observations.remove(key)
        return state(key, request)
    }

    private fun postSuccess(key: String, details: RequestDetails) {
        val entries = when (details) {
            is RequestDetails.Pay -> listOf(Entry(details.debit, Side.D, details.amount), Entry(details.credit, Side.C, details.amount))
            is RequestDetails.Refund -> {
                val pay = requests.getValue(details.original).details as RequestDetails.Pay
                listOf(Entry(pay.credit, Side.D, details.amount), Entry(pay.debit, Side.C, details.amount))
            }
        }
        check(ledger.post("@$key", Journal(details.currency, entries)) == "OK POST @$key")
    }

    fun crash(): String {
        observations.clear()
        return "OK CRASH"
    }

    fun enqueue(key: String, result: ScriptResult): String = remote.enqueue(key, result)
    fun remote(key: String): String = remote.describe(key)
    private fun state(key: String, request: PaymentRequest): String = "STATE $key ${request.state}"
}
