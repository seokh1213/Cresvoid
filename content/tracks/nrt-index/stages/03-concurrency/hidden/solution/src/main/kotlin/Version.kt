data class Version(val sequence: Long, val term: Long)
data class VersionedMutation(val mutation: Mutation, val version: Version)

fun VersionedMutation?.describe(): String {
    val record = this ?: return "MISSING"
    val version = record.version
    return when (val mutation = record.mutation) {
        is Mutation.Put -> "${version.sequence} ${version.term} ${mutation.words.joinToString(" ")}"
        is Mutation.Delete -> "DELETED ${version.sequence} ${version.term}"
    }
}
