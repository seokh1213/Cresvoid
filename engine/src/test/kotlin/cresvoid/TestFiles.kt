package cresvoid

import cresvoid.content.Stage
import java.nio.file.Files
import java.nio.file.Path

fun withTempDir(action: (Path) -> Unit) {
    val dir = Files.createTempDirectory("cresvoid-unit-")
    try {
        action(dir)
    } finally {
        dir.toFile().deleteRecursively()
    }
}

fun stageIn(dir: Path, vararg files: Pair<String, String>): Stage {
    Files.createDirectories(dir)
    files.forEach { (name, content) ->
        val file = dir.resolve(name)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }
    return Stage(
        number = 1,
        dir = dir.fileName.toString(),
        title = "단계",
        targetMinutes = 0,
        path = dir,
        documentPath = dir.resolve("spec.md"),
    )
}
