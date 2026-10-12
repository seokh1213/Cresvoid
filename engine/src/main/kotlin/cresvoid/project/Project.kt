package cresvoid.project

import cresvoid.CresvoidError
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

class Project(val root: Path) {
    val tracksDir: Path = root.resolve("content").resolve("tracks")
    val practiceDir: Path = root.resolve("practice")
    val progressDir: Path = practiceDir.resolve("progress")

    fun rel(path: Path): String {
        if (!path.isAbsolute) return path.toString()
        val relative = root.normalize().relativize(path.normalize()).toString()
        return when {
            relative.isEmpty() -> "."
            relative.startsWith("..") -> path.toString()
            else -> relative
        }
    }

    fun abs(path: String): Path {
        val candidate = Path(path)
        return if (candidate.isAbsolute) candidate else root.resolve(candidate).normalize()
    }

    companion object {
        fun locate(configuredRoot: String?): Project {
            val fromEnvironment = configuredRoot?.takeIf { it.isNotEmpty() }?.let(::resolveFromWorkingDirectory)
            val candidates = generateSequence(workingDirectory) { it.parent } + listOfNotNull(fromEnvironment)
            val root = candidates.firstOrNull { it.resolve("content").resolve("tracks").isDirectory() }
                ?: throw CresvoidError("content/tracks 디렉터리를 찾지 못했습니다. cresvoid 프로젝트 안에서 실행하세요")
            return Project(root)
        }
    }
}
