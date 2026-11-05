package lab

import org.apache.spark.TaskContext
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

fun publishRecord(root: Path, id: Int) {
    val target = root.resolve("published").resolve(TaskContext.get().partitionId().toString())
    try {
        Files.writeString(target, "$id\n", CREATE_NEW, WRITE)
    } catch (_: FileAlreadyExistsException) {
        return
    }
}

fun readPublished(root: Path): List<String> =
    Files.list(root.resolve("published")).use { files ->
        files.sorted().toList().flatMap { Files.readAllLines(it) }
    }
