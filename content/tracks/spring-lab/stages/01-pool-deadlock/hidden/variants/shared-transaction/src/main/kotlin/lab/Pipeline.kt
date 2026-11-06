package lab

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

class PlannedRollback : RuntimeException()

@Service
open class AuditWriter(private val jdbc: JdbcTemplate) {
    @Transactional(propagation = Propagation.NESTED)
    open fun write(id: String) {
        jdbc.update("INSERT INTO audit_log(id) VALUES (?)", id)
    }
}

@Service
open class Pipeline(private val jdbc: JdbcTemplate, private val audit: AuditWriter) {
    @Transactional
    open fun process(job: Job) {
        jdbc.update("INSERT INTO orders(id) VALUES (?)", job.id)
        job.batch.ready()
        try {
            job.measureAudit { audit.write(job.id) }
        } finally {
            // Retain outer resources until every inner attempt has an outcome.
            job.batch.finishedAttempt()
        }
        if (job.rollback) throw PlannedRollback()
    }
}
