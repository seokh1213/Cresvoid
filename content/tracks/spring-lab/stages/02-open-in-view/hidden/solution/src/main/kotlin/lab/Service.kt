package lab

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class PurchaseSnapshot(val id: Long, val owner: String, val items: List<String>)

@Service
class PurchaseReader {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional(readOnly = true)
    fun read(id: Long): PurchaseSnapshot {
        val purchase = entityManager.find(Purchase::class.java, id) ?: error("purchase missing")
        return PurchaseSnapshot(purchase.id, purchase.owner, purchase.items.toList())
    }
}

@Service
class PurchaseService(private val reader: PurchaseReader, private val external: SlowExternal) {
    fun details(id: Long): PurchaseReply {
        val snapshot = reader.read(id)
        val result = external.call(id)
        return PurchaseReply(snapshot.id, snapshot.owner, snapshot.items, result)
    }
}
