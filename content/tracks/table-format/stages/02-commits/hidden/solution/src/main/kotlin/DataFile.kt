class DataFile(override val name: String, val partition: PartitionValue, val rows: List<Row>) : StoredObject {
    val rowCount: Int get() = rows.size
    val lowerBounds: List<Value> = rows.first().indices.map { column -> rows.minOf { it[column] } }
    val upperBounds: List<Value> = rows.first().indices.map { column -> rows.maxOf { it[column] } }
}
