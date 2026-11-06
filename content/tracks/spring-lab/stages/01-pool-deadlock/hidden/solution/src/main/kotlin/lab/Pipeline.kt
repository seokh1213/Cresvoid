package lab

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

class PlannedRollback : RuntimeException()

@Service
open class AuditWriter(private val jdbc: JdbcTemplate) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun write(id: String) {
        jdbc.update("INSERT INTO audit_log(id) VALUES (?)", id)
    }
}

@Service
open class OrderWriter(private val jdbc: JdbcTemplate) {
    @Transactional
    open fun write(job: Job) {
        jdbc.update("INSERT INTO orders(id) VALUES (?)", job.id)
        if (job.rollback) throw PlannedRollback()
    }
}

@Service
class Pipeline(private val orders: OrderWriter, private val audit: AuditWriter) {
    fun process(job: Job) {
        job.batch.ready()
        try {
            job.measureAudit { audit.write(job.id) }
        } finally {
            job.batch.finishedAttempt()
        }
        orders.write(job)
    }
}
