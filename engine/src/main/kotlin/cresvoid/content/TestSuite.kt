package cresvoid.content

import java.time.Duration

private val defaultTestTimeout: Duration = Duration.ofSeconds(10)

class TestSuite(val timeoutSec: Double?, val tests: List<TestCase>) {
    fun timeoutFor(test: TestCase): Duration {
        val seconds = test.timeoutSec?.takeIf { it > 0 } ?: timeoutSec?.takeIf { it > 0 } ?: return defaultTestTimeout
        return Duration.ofNanos((seconds * 1e9).toLong())
    }
}

class TestCase(
    val name: String,
    val refs: List<String>,
    val quote: String,
    val input: List<String>?,
    val expected: List<String>?,
    val gen: List<String>?,
    val repeat: Int?,
    val timeoutSec: Double?,
) {
    val repeatCount: Int get() = repeat?.takeIf { it > 0 } ?: 1
}

fun loadTestSuite(stage: Stage): TestSuite = readContentJson(stage.testsPath) { fields ->
    TestSuite(
        timeoutSec = fields.optionalDouble("timeout_sec"),
        tests = fields.objectList("tests") { test ->
            TestCase(
                name = test.string("name"),
                refs = test.stringList("refs"),
                quote = test.string("quote"),
                input = test.optionalStringList("input"),
                expected = test.optionalStringList("expected"),
                gen = test.optionalStringList("gen"),
                repeat = test.optionalInt("repeat"),
                timeoutSec = test.optionalDouble("timeout_sec"),
            )
        },
    )
}
