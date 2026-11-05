data class SettlementRow(val key: String, val currency: String, val amount: Long)
data class SettlementValue(val currency: String, val amount: Long)

sealed interface Discrepancy {
    fun render(key: String): String

    data class Duplicate(val count: Int) : Discrepancy {
        override fun render(key: String): String = "DUPLICATE $key $count"
    }
    data class MissingRemote(val expected: SettlementValue) : Discrepancy {
        override fun render(key: String): String = "MISSING_REMOTE $key ${expected.currency} ${expected.amount}"
    }
    data class MissingLocal(val actual: SettlementValue) : Discrepancy {
        override fun render(key: String): String = "MISSING_LOCAL $key ${actual.currency} ${actual.amount}"
    }
    data class CurrencyMismatch(val expected: SettlementValue, val actual: SettlementValue) : Discrepancy {
        override fun render(key: String): String =
            "CURRENCY_MISMATCH $key ${expected.currency} ${expected.amount} ${actual.currency} ${actual.amount}"
    }
    data class AmountMismatch(val expected: SettlementValue, val actual: SettlementValue) : Discrepancy {
        override fun render(key: String): String = "AMOUNT_MISMATCH $key ${expected.currency} ${expected.amount} ${actual.amount}"
    }
}
