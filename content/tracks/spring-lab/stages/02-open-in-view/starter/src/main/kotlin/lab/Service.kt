package lab

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PurchaseReader {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional(readOnly = true)
    fun read(id: Long): Purchase = entityManager.find(Purchase::class.java, id)
        ?: error("purchase missing")
}

@Service
class PurchaseService(private val reader: PurchaseReader, private val external: SlowExternal) {
    fun details(id: Long): PurchaseReply {
        val purchase = reader.read(id)
        val result = external.call(id)
        return PurchaseReply(purchase.id, purchase.owner, purchase.items.toList(), result)
    }
}
