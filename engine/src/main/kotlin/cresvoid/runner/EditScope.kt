package cresvoid.runner

import cresvoid.CresvoidError
import cresvoid.content.Stage
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

private val localDirectories = setOf("build", ".gradle", ".kotlin", ".idea")

fun checkEditScope(stage: Stage, candidate: Path) {
    val manifest = stage.hiddenDir.resolve("allowed-edits.txt")
    if (!manifest.exists()) return

    val allowed = manifest.readLines().map(String::trim).filter { it.isNotEmpty() }.toSet()
    if (allowed.any { !it.startsWith("src/main/kotlin/") || !it.endsWith(".kt") || ".." in it.split('/') }) {
        throw CresvoidError("수정 허용 파일 목록이 잘못되었습니다: ${stage.dir}")
    }

    val starterFiles = projectFiles(stage.starterDir)
    if (!starterFiles.keys.containsAll(allowed)) {
        throw CresvoidError("수정 허용 파일이 시작 코드에 없습니다: ${stage.dir}")
    }
    val candidateFiles = projectFiles(candidate)
    val changed = (starterFiles.keys + candidateFiles.keys).sorted().firstOrNull { path ->
        path !in allowed && !starterFiles[path].contentEqualsOrMissing(candidateFiles[path])
    }
    if (changed != null) {
        throw CresvoidError("수정 범위 밖의 파일이 바뀌었습니다: $changed. ${stage.documentPath.fileName}의 수정 대상을 확인하세요")
    }
}

private fun ByteArray?.contentEqualsOrMissing(other: ByteArray?): Boolean =
    this != null && other != null && this.contentEquals(other)

private fun projectFiles(project: Path): Map<String, ByteArray> =
    project.toFile().walkTopDown()
        .onEnter { directory -> directory == project.toFile() || directory.name !in localDirectories }
        .filter(File::isFile)
        .filterNot { it.name == ".DS_Store" }
        .associate { file ->
            val path = project.relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            path to file.readBytes()
        }
