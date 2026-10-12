package cresvoid.progress

import cresvoid.CresvoidError
import cresvoid.json.JsonError
import cresvoid.json.KeyRule
import cresvoid.json.decodeJsonObject
import cresvoid.json.parseJson
import cresvoid.json.toIndentedJson
import cresvoid.project.readUtf8
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.exists

class FileProgressRepository(private val progressDir: Path, private val clock: Clock) : ProgressRepository {
    private val path = progressDir.resolve("progress.json")
    private val lockPath = progressDir.resolve("progress.lock")
    private val backupsDir = progressDir.resolve("backups")

    override fun read(): StoredProgress {
        val text = try {
            path.readUtf8()
        } catch (_: NoSuchFileException) {
            return StoredProgress.Readable(Progress())
        }
        return try {
            val parsed = parseJson(text, KeyRule.ANY)
            if (parsed.hasTrailingContent) throw JsonError("JSON 값 뒤에 다른 내용이 있습니다")
            StoredProgress.Readable(decodeJsonObject(parsed.value, location = "", strict = false, ::decodeProgress))
        } catch (error: JsonError) {
            StoredProgress.Unreadable(error.message)
        }
    }

    override fun load(): Progress = when (val stored = read()) {
        is StoredProgress.Readable -> stored.progress
        is StoredProgress.Unreadable ->
            throw CresvoidError("진행 기록을 읽지 못했습니다: $path: ${stored.problem}\n사본을 남기고 모든 기록을 지우려면: cresvoid reset")
    }

    override fun <R> updateWith(change: (Progress) -> Updated<R>): Updated<R> = locked {
        val latest = load()
        val updated = change(latest)
        if (updated.progress != latest) write(updated.progress)
        updated
    }

    override fun replaceKeepingBackup(change: (Progress) -> Progress): Path? = locked {
        val latest = load()
        val replaced = change(latest)
        if (replaced == latest) return@locked null
        writeKeepingBackup(replaced)
    }

    override fun clearKeepingBackup(): Path? = locked {
        if (read() == StoredProgress.Readable(Progress())) return@locked null
        writeKeepingBackup(Progress())
    }

    private fun writeKeepingBackup(progress: Progress): Path {
        val backup = unusedBackupPath(LocalDateTime.now(clock))
        Files.createDirectories(backupsDir)
        Files.copy(path, backup)
        write(progress)
        return backup
    }

    private fun unusedBackupPath(now: LocalDateTime): Path {
        val stamp = backupStamp.format(now)
        return generateSequence(1) { it + 1 }
            .map { attempt -> backupsDir.resolve(if (attempt == 1) "progress-$stamp.json" else "progress-$stamp-$attempt.json") }
            .first { !it.exists() }
    }

    private fun <T> locked(action: () -> T): T {
        Files.createDirectories(progressDir)
        return processLock.withLock {
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { action() }
            }
        }
    }

    private fun write(progress: Progress) {
        val temporary = Files.createTempFile(progressDir, "progress-", ".tmp")
        try {
            Files.write(temporary, (encodeProgress(progress).toIndentedJson() + "\n").toByteArray(Charsets.UTF_8))
            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-r--r--"))
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private companion object {
        val processLock = ReentrantLock()
        val backupStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
