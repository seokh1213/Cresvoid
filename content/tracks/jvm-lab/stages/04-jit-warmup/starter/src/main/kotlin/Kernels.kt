enum class Candidate { A, B }

object Kernels {
    fun a(seed: Int): Int {
        var value = seed
        for (step in 0 until 64) {
            value = (value xor (value ushr 13)) * 1664525 + 1013904223
        }
        return value
    }

    fun b(seed: Int): Int {
        var value = seed
        var remaining = 64
        while (remaining > 0) {
            value = (value xor (value ushr 13)) * 1664525 + 1013904223
            remaining--
        }
        return value
    }
}
