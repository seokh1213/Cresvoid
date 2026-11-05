import java.time.LocalDate
import java.time.YearMonth

data class OrderRow(
    val seller: String,
    val orderedOn: LocalDate,
    val quantity: Int,
    val unitPrice: Int,
    val feeBps: Int,
    val orderId: String,
) {
    init {
        require(seller.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")))
        require(quantity in 1..100_000)
        require(unitPrice in 1..100_000)
        require(feeBps in 0..10_000)
        require(orderId.isNotBlank())
    }

    val key: SettlementKey
        get() = SettlementKey(seller, YearMonth.from(orderedOn))

    fun gross(): Long = Math.multiplyExact(quantity.toLong(), unitPrice.toLong())

    fun fee(): Long = Math.multiplyExact(gross(), feeBps.toLong()) / 10_000

    // 구형 파일의 대조값이며 지급액 계산에는 사용하지 않는다.
    fun legacyGross(): Long = (quantity * unitPrice).toLong()
}

data class SettlementKey(val seller: String, val month: YearMonth)

data class SettlementTotals(
    val rowCount: Int = 0,
    val gross: Long = 0,
    val fee: Long = 0,
) {
    val payable: Long
        get() = gross - fee

    fun add(row: OrderRow): SettlementTotals = SettlementTotals(
        rowCount = Math.incrementExact(rowCount),
        gross = Math.addExact(gross, row.gross()),
        fee = Math.addExact(fee, row.fee()),
    )
}

enum class AggregationMethod { FOLD, GROUP }

class SettlementCalculator {
    fun calculate(
        rows: List<OrderRow>,
        method: AggregationMethod,
    ): Map<SettlementKey, SettlementTotals> = when (method) {
        AggregationMethod.FOLD -> foldRows(rows)
        AggregationMethod.GROUP -> groupRows(rows)
    }

    private fun foldRows(rows: List<OrderRow>): Map<SettlementKey, SettlementTotals> =
        rows.groupingBy { it.key }.fold(SettlementTotals()) { totals, row ->
            totals.add(row)
        }

    private fun groupRows(rows: List<OrderRow>): Map<SettlementKey, SettlementTotals> {
        val rowsBySettlement = rows.groupBy { it.key }
        return rowsBySettlement.mapValues { (_, sellerRows) ->
            sellerRows.fold(SettlementTotals()) { totals, row -> totals.add(row) }
        }
    }

    fun audit(rows: List<OrderRow>, seller: String): LegacyAudit {
        val sellerRows = rows.filter { it.seller == seller }
        val exactGross = sellerRows.fold(0L) { total, row ->
            Math.addExact(total, row.gross())
        }
        val legacyGross = sellerRows.fold(0L) { total, row ->
            Math.addExact(total, row.legacyGross())
        }
        return LegacyAudit(seller, exactGross, legacyGross, exactGross.toInt())
    }
}

data class LegacyAudit(
    val seller: String,
    val exactGross: Long,
    val legacyGross: Long,
    val wireCheck: Int,
)
