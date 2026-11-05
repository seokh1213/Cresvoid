package lab

import org.apache.spark.TaskContext
import org.apache.spark.api.java.function.VoidFunction
import org.apache.spark.util.LongAccumulator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

class InjectedWriteFailure : RuntimeException("injected-write-failure")

class ExternalWrite(
    private val directory: String,
    private val failAfter: Int?,
    private val accumulator: LongAccumulator,
) : VoidFunction<Iterator<Int>> {
    override fun call(rows: Iterator<Int>) {
        val root = Path.of(directory)
        Files.writeString(root.resolve("attempts.log"), "${TaskContext.get().attemptNumber()}\n", CREATE, APPEND)
        var written = 0
        rows.forEachRemaining { id ->
            Files.writeString(root.resolve("raw.log"), "$id\n", CREATE, APPEND)
            publishRecord(root, id)
            accumulator.add(1)
            written += 1
            if (written == failAfter) throw InjectedWriteFailure()
        }
    }
}
