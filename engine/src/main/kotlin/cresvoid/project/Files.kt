package cresvoid.project

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.setPosixFilePermissions

private val executePermissions = setOf(
    PosixFilePermission.OWNER_EXECUTE,
    PosixFilePermission.GROUP_EXECUTE,
    PosixFilePermission.OTHERS_EXECUTE,
)

val workingDirectory: Path by lazy {
    val physical = Path("").toAbsolutePath()
    val logical = System.getenv("PWD")
        ?.let(::Path)
        ?.takeIf { it.isAbsolute && isSameDirectory(it, physical) }
    logical ?: physical
}

fun resolveFromWorkingDirectory(path: String): Path = workingDirectory.resolve(path).normalize()

fun Path.isExecutableFile(): Boolean =
    isRegularFile() && try {
        getPosixFilePermissions().any { it in executePermissions }
    } catch (_: IOException) {
        false
    }

fun Path.hasEntries(): Boolean =
    isDirectory() && Files.list(this).use { entries -> entries.findAny().isPresent }

fun isSameDirectory(first: Path, second: Path): Boolean =
    first.exists() && second.exists() && try {
        Files.isSameFile(first, second)
    } catch (_: IOException) {
        false
    }

fun Path.readUtf8(): String = String(Files.readAllBytes(this), Charsets.UTF_8)

fun copyTree(source: Path, target: Path) {
    val root = source.toRealPath()
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
            if (dir != root && dir.name == "build") return FileVisitResult.SKIP_SUBTREE
            Files.createDirectories(target.resolve(root.relativize(dir)))
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
            if (file.name == ".DS_Store") return FileVisitResult.CONTINUE
            val destination = target.resolve(root.relativize(file))
            Files.write(destination, Files.readAllBytes(file))
            destination.setPosixFilePermissions(file.getPosixFilePermissions())
            return FileVisitResult.CONTINUE
        }
    })
}
