private const val HASH_MODULUS = 4_294_967_296L

fun routingHash(routing: String): Long =
    routing.fold(0L) { hash, character -> (hash * 31 + character.code) % HASH_MODULUS }
