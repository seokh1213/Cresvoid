import java.math.BigDecimal
import java.text.MessageFormat
import java.util.Locale

enum class DocumentChannel { NOTICE, EXPORT }

private enum class QuoteField {
    PRODUCT, QUANTITY, ORIGINAL, DISCOUNT, PAYABLE, RATE;

    fun read(quotation: PriceQuotation): Any = when (this) {
        PRODUCT -> quotation.productId
        QUANTITY -> quotation.quantity.toString()
        ORIGINAL -> quotation.originalAmount
        DISCOUNT -> quotation.discountAmount
        PAYABLE -> quotation.payableAmount
        RATE -> BigDecimal.valueOf(quotation.discountBasisPoints.toLong(), 2)
    }
}

private class DocumentLayout(
    private val prototype: MessageFormat,
    private val fields: List<QuoteField>,
) {
    fun render(quotation: PriceQuotation): String = prototype.format(arguments(quotation))

    fun bind(quotation: PriceQuotation): BoundDocument {
        val message = MessageFormat(prototype.toPattern())
        return BoundDocument(message, arguments(quotation))
    }

    private fun arguments(quotation: PriceQuotation): Array<Any> =
        fields.map { it.read(quotation) }.toTypedArray()
}

class BoundDocument(
    private val message: MessageFormat,
    private val arguments: Array<Any>,
) {
    fun render(): String = message.format(arguments)
}

class PreparedDocuments(private val channels: Map<DocumentChannel, BoundDocument>) {
    fun render(channel: DocumentChannel): String = channels.getValue(channel).render()
}

private class RegisteredLayout(noticePrefix: String, rowPrefix: String) {
    private val channels = mapOf(
        DocumentChannel.NOTICE to DocumentLayout(
            MessageFormat(
                "$noticePrefix {0} quantity={1} total={2,number,#,##0}원 discount={3,number,0.00}%",
                Locale.ROOT,
            ),
            listOf(QuoteField.PRODUCT, QuoteField.QUANTITY, QuoteField.PAYABLE, QuoteField.RATE),
        ),
        DocumentChannel.EXPORT to DocumentLayout(
            MessageFormat(
                "$rowPrefix|{0}|{1}|{2,number,#,##0}|{3,number,#,##0}|{4,number,#,##0}|{5,number,0.00}",
                Locale.ROOT,
            ),
            listOf(
                QuoteField.PRODUCT, QuoteField.QUANTITY, QuoteField.ORIGINAL,
                QuoteField.DISCOUNT, QuoteField.PAYABLE, QuoteField.RATE,
            ),
        ),
    )

    fun render(quotation: PriceQuotation, channel: DocumentChannel): String =
        channels.getValue(channel).render(quotation)

    fun bind(quotation: PriceQuotation): PreparedDocuments =
        PreparedDocuments(channels.mapValues { (_, layout) -> layout.bind(quotation) })
}

class PriceDocuments {
    private val layouts = linkedMapOf("default" to RegisteredLayout("NOTICE", "ROW"))
    private var selected = "default"

    fun register(layoutId: String, noticePrefix: String, rowPrefix: String) {
        layouts[layoutId] = RegisteredLayout(noticePrefix, rowPrefix)
    }

    fun select(layoutId: String): Boolean {
        if (layoutId !in layouts) return false
        selected = layoutId
        return true
    }

    fun render(quotation: PriceQuotation, channel: DocumentChannel): String =
        layouts.getValue(selected).render(quotation, channel)

    fun prepare(quotation: PriceQuotation): PreparedDocuments =
        layouts.getValue(selected).bind(quotation)
}
