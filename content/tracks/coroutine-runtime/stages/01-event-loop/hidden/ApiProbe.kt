import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun main() {
    val order = mutableListOf<String>()
    val runtime = VirtualRuntime()
    val queued = object : Continuation<Unit> {
        override val context: CoroutineContext = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            result.getOrThrow()
            order += "queued:${runtime.now}"
        }
    }

    runtime.run {
        order += "start:${runtime.now}"
        runtime.dispatch(queued)
        delay(0)
        order += "after-zero:${runtime.now}"
        delay(3)
        order += "after-three:${runtime.now}"
    }
    check(order == listOf("start:0", "queued:0", "after-zero:0", "after-three:3"))
    check(runtime.now == 3L)

    val completed = mutableListOf<String>()
    val nested = VirtualRuntime()
    nested.run {
        val child = async {
            delay(2)
            completed += "child:${nested.now}"
            "ready"
        }
        completed += "parent:${nested.now}"
        completed += "await:${child.await()}:${nested.now}"
    }
    check(completed == listOf("parent:0", "child:2", "await:ready:2"))
}
