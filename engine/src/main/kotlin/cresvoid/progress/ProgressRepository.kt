package cresvoid.progress

import java.nio.file.Path

class Updated<out R>(val progress: Progress, val result: R)

sealed interface StoredProgress {
    data class Readable(val progress: Progress) : StoredProgress

    data class Unreadable(val problem: String) : StoredProgress
}

interface ProgressRepository {
    fun read(): StoredProgress

    fun load(): Progress

    fun update(transform: (Progress) -> Progress): Progress = updateWith { latest -> Updated(transform(latest), Unit) }.progress

    fun <R> updateWith(change: (Progress) -> Updated<R>): Updated<R>

    fun replaceKeepingBackup(change: (Progress) -> Progress): Path?

    fun clearKeepingBackup(): Path?
}
