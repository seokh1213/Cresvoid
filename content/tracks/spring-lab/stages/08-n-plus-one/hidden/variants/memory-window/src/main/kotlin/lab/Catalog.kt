package lab

import jakarta.persistence.EntityManager
import org.hibernate.jpa.HibernateHints
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Catalog(private val entityManager: EntityManager) {
    @Transactional(readOnly = true)
    fun page(page: Int, size: Int): OrderPage {
        val total = entityManager.createQuery("select count(o) from PurchaseOrder o", Long::class.javaObjectType)
            .singleResult
        val orders = entityManager.createQuery(
            "select o from PurchaseOrder o left join fetch o.items order by o.id",
            PurchaseOrder::class.java,
        ).setHint(HibernateHints.HINT_LIMIT_IN_MEMORY, true)
            .setFirstResult(page * size)
            .setMaxResults(size)
            .resultList
        return OrderPage(total, orders.map { it.toView() })
    }
}
