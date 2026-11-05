import java.io.Closeable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

class CatalogFileStore : Closeable {
    private val directory: Path = Files.createTempDirectory("catalog-export-")
    private val files = mutableMapOf<String, Path>()

    fun save(id: String, content: String): Long {
        val path = directory.resolve(id)
        Files.write(path, content.toByteArray(Charsets.UTF_8))
        files[id] = path
        return Files.size(path)
    }

    fun size(id: String): Long = Files.size(path(id))

    fun text(id: String): String = Files.readString(path(id), Charsets.UTF_8)

    fun open(id: String): InputStream = Files.newInputStream(path(id))

    private fun path(id: String): Path = files.getValue(id)

    override fun close() {
        for (path in files.values) {
            Files.deleteIfExists(path)
        }
        Files.deleteIfExists(directory)
    }
}
