private val POSITIVE_INTEGER_FORMAT = Regex("[1-9][0-9]*")
private const val MAX_SIZE = 1000
private const val MAX_SHARD_SIZE = 10_000
private const val DEFAULT_SIZE = 10
private val PARAMETER_NAMES = setOf("size", "shard_size")

data class TermsRequest(val size: Int, val shardSize: Int)

fun parseTermsRequest(arguments: List<String>): TermsRequest {
    val parameters = parseParameters(arguments, PARAMETER_NAMES)
    val size = parameters["size"]?.let { parseBoundedInt(it, POSITIVE_INTEGER_FORMAT, MAX_SIZE) } ?: DEFAULT_SIZE
    val requestedShardSize = parameters["shard_size"]?.let { parseBoundedInt(it, POSITIVE_INTEGER_FORMAT, MAX_SHARD_SIZE) }
        ?: defaultShardSize(size)
    return TermsRequest(size, shardSize = maxOf(requestedShardSize, size))
}

private fun defaultShardSize(size: Int): Int = (size * 1.5 + 10).toInt()
