package lab

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "import_row", uniqueConstraints = [UniqueConstraint(columnNames = ["run_key", "ordinal_no"])])
open class ImportRow(
    @field:Column(name = "run_key", nullable = false) open var runKey: String = "",
    @field:Column(name = "ordinal_no", nullable = false) open var ordinal: Int = 0,
    @field:Column(nullable = false) open var payload: String = "",
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
}
