package lab

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

fun publishRecord(root: Path, id: Int) {
    Files.writeString(root.resolve("published.log"), "$id\n", CREATE, APPEND)
}

fun readPublished(root: Path): List<String> = Files.readAllLines(root.resolve("published.log"))
