package lab

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicBoolean

@Component
class Fixture(private val jdbc: JdbcTemplate) : ApplicationRunner {
    private val readiness = AtomicBoolean(false)
    val ready: Boolean get() = readiness.get()

    @Transactional
    override fun run(args: ApplicationArguments) {
        for (id in 1L..137L) insertOrder(id)
        readiness.set(true)
    }

    @Transactional
    fun append(): Long {
        val id = jdbc.queryForObject("select coalesce(max(id), 0) + 1 from purchase_order", Long::class.java)!!
        insertOrder(id)
        return id
    }

    private fun insertOrder(id: Long) {
        jdbc.update("insert into purchase_order (id, state) values (?, ?)", id, if (id % 3L == 0L) "CLOSED" else "OPEN")
        for (index in 1L..(id % 5L)) {
            jdbc.update("insert into order_item (id, order_id) values (?, ?)", id * 10L + index, id)
        }
    }
}
