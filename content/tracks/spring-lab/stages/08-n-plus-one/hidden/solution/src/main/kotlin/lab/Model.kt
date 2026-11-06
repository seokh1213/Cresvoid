package lab

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table

@Entity
@Table(name = "purchase_order")
open class PurchaseOrder(
    @field:Id open var id: Long = 0,
    open var state: String = "OPEN",
) {
    @field:OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @field:OrderBy("id ASC")
    open var items: MutableList<OrderItem> = mutableListOf()
}

@Entity
@Table(name = "order_item")
open class OrderItem(
    @field:Id open var id: Long = 0,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "order_id", nullable = false)
    open var order: PurchaseOrder? = null,
)

data class OrderView(val id: Long, val state: String, val itemIds: List<Long>)
data class OrderPage(val total: Long, val orders: List<OrderView>)

fun PurchaseOrder.toView(): OrderView = OrderView(id, state, items.map { it.id }.sorted())
