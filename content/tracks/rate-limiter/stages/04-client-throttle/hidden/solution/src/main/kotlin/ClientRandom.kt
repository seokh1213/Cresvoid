private const val RANDOM_MODULUS = 4_294_967_296L

class ClientRandom(seed: Long) {
    private var state = seed

    fun draw(): Long {
        state = (1_664_525L * state + 1_013_904_223L) % RANDOM_MODULUS
        return state
    }
}

data class DropProbability(val numerator: Long, val denominator: Long) {
    fun rejects(draw: Long): Boolean =
        draw.toBigInteger() * denominator.toBigInteger() < RANDOM_MODULUS.toBigInteger() * numerator.toBigInteger()
}
