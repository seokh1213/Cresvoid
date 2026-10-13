private suspend fun PullScope<String?>.nestedYield(events: MutableList<String>) {
    var local = "before"
    events += "nested-start"
    yield(null)
    local += "-after"
    events += "nested-resume"
    yield(local)
}

fun main() {
    val events = mutableListOf<String>()
    val numbers = pullSequence<Int> {
        events += "start"
        yield(10)
        events += "middle"
        yield(20)
        events += "end"
    }
    check(events.isEmpty())
    val first = numbers.iterator()
    val second = numbers.iterator()
    check(events.isEmpty())
    check(first.hasNext())
    check(first.hasNext())
    check(events == listOf("start"))
    check(first.next() == 10)
    check(events == listOf("start"))
    check(second.next() == 10)
    check(events == listOf("start", "start"))
    check(first.next() == 20)
    check(second.next() == 20)
    check(events == listOf("start", "start", "middle", "middle"))
    check(!first.hasNext())
    check(!first.hasNext())
    check(events.last() == "end")
    check(runCatching { first.next() }.exceptionOrNull() is NoSuchElementException)
    check(events.count { it == "end" } == 1)

    val nestedEvents = mutableListOf<String>()
    val nullable = pullSequence<String?> { nestedYield(nestedEvents) }.iterator()
    check(nullable.hasNext())
    check(nullable.next() == null)
    check(nestedEvents == listOf("nested-start"))
    check(nullable.next() == "before-after")
    check(nestedEvents == listOf("nested-start", "nested-resume"))
    check(!nullable.hasNext())

    val local = pullSequence<Int> {
        var count = 0
        repeat(3) {
            count++
            yield(count)
        }
    }
    val left = local.iterator()
    val right = local.iterator()
    check(left.next() == 1)
    check(left.next() == 2)
    check(right.next() == 1)
    check(left.next() == 3)
    check(right.next() == 2)

    val failure = IllegalArgumentException("producer failure")
    val failingSequence = pullSequence<Int> {
        yield(7)
        throw failure
    }
    val broken = failingSequence.iterator()
    val independent = failingSequence.iterator()
    check(broken.next() == 7)
    check(runCatching { broken.hasNext() }.exceptionOrNull() === failure)
    check(!broken.hasNext())
    check(runCatching { broken.next() }.exceptionOrNull() is NoSuchElementException)
    check(independent.next() == 7)
    check(runCatching { independent.next() }.exceptionOrNull() === failure)
    check(!independent.hasNext())

    val empty = pullSequence<Int> {}.iterator()
    check(!empty.hasNext())
    check(runCatching { empty.next() }.exceptionOrNull() is NoSuchElementException)

    var afterLastYield = false
    val abandoned = pullSequence<Int> {
        yield(1)
        afterLastYield = true
    }.iterator()
    check(abandoned.next() == 1)
    check(!afterLastYield)
}
