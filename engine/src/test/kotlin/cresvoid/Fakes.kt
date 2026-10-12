package cresvoid

import cresvoid.progress.Progress
import cresvoid.progress.ProgressRepository
import cresvoid.progress.StoredProgress
import cresvoid.progress.Updated
import cresvoid.terminal.Console
import java.nio.file.Path

class InMemoryProgressRepository(var progress: Progress = Progress()) : ProgressRepository {
    override fun read(): StoredProgress = StoredProgress.Readable(progress)

    override fun load(): Progress = progress

    override fun <R> updateWith(change: (Progress) -> Updated<R>): Updated<R> = change(progress).also { progress = it.progress }

    override fun replaceKeepingBackup(change: (Progress) -> Progress): Path? {
        progress = change(progress)
        return null
    }

    override fun clearKeepingBackup(): Path? {
        progress = Progress()
        return null
    }
}

class RecordingConsole : Console {
    private val output = StringBuilder()

    val text: String get() = output.toString()

    override fun print(text: String) {
        output.append(text)
    }

    override fun println(text: String) {
        output.append(text).append('\n')
    }

    override fun write(bytes: ByteArray) {
        output.append(String(bytes, Charsets.UTF_8))
    }

    override fun error(text: String) {
        output.append(text).append('\n')
    }
}
