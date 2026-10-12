sealed interface ContentFile : StoredObject {
    val partition: PartitionValue
    val rowCount: Int
}

class DataFile(override val name: String, override val partition: PartitionValue, val rows: List<Row>) : ContentFile {
    override val rowCount: Int get() = rows.size
    val lowerBounds: List<Value> = rows.first().indices.map { column -> rows.minOf { it[column] } }
    val upperBounds: List<Value> = rows.first().indices.map { column -> rows.maxOf { it[column] } }
}

sealed interface DeleteFile : ContentFile

class PositionDeleteFile(override val name: String, val target: DataFile, val positions: List<Int>) : DeleteFile {
    override val partition: PartitionValue get() = target.partition
    override val rowCount: Int get() = positions.size
}

class EqualityDeleteFile(
    override val name: String,
    val columnList: String,
    val columns: List<Int>,
    override val partition: PartitionValue,
    val rows: List<Row>,
) : DeleteFile {
    override val rowCount: Int get() = rows.size
}
