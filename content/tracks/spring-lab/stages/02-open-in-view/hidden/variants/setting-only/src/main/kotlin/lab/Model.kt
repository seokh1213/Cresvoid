package lab

import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

@Entity
@Table(name = "purchase_record")
open class Purchase(
    @field:Id open var id: Long = 0,
    open var owner: String = "",
    @field:ElementCollection(fetch = FetchType.LAZY)
    @field:OrderColumn(name = "item_position")
    open var items: MutableList<String> = mutableListOf(),
)

data class PurchaseReply(val id: Long, val owner: String, val items: List<String>, val external: String)

@Component
class Seed : ApplicationRunner {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional
    override fun run(args: ApplicationArguments) {
        for (id in 1L..40L) {
            entityManager.persist(Purchase(id, "reader-$id", mutableListOf("book-$id", "pen-$id")))
        }
    }
}
