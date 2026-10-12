sealed interface StoredObject {
    val name: String
}

class ObjectStore(initialMetadata: TableMetadata) {
    private val objects = HashMap<String, StoredObject>()
    private var pointer = initialMetadata.name

    init {
        write(initialMetadata)
    }

    fun write(stored: StoredObject) {
        objects[stored.name] = stored
    }

    fun currentMetadata(): TableMetadata = objects.getValue(pointer) as TableMetadata

    fun swap(expected: String, replacement: String): Boolean {
        if (pointer != expected) return false
        pointer = replacement
        return true
    }

    fun names(): Set<String> = objects.keys

    fun objects(): Collection<StoredObject> = objects.values

    fun deleteAll(names: Collection<String>): List<StoredObject> = names.mapNotNull(objects::remove)
}
