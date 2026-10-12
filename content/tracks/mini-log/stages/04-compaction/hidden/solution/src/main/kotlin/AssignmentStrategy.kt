class SubscribedTopic(val name: String, val partitionCount: Int) {
    val partitions: List<TopicPartition> get() = List(partitionCount) { TopicPartition(name, it) }
}

sealed interface AssignmentStrategy {
    fun assign(members: List<String>, topics: List<SubscribedTopic>): Map<String, List<TopicPartition>>

    data object Range : AssignmentStrategy {
        override fun assign(members: List<String>, topics: List<SubscribedTopic>): Map<String, List<TopicPartition>> {
            val assignment = members.associateWith { ArrayList<TopicPartition>() }
            for (topic in topics) {
                val quotient = topic.partitionCount / members.size
                val remainder = topic.partitionCount % members.size
                members.forEachIndexed { index, member ->
                    val first = index * quotient + minOf(index, remainder)
                    val count = if (index < remainder) quotient + 1 else quotient
                    assignment.getValue(member) += topic.partitions.subList(first, first + count)
                }
            }
            return assignment
        }
    }

    data object RoundRobin : AssignmentStrategy {
        override fun assign(members: List<String>, topics: List<SubscribedTopic>): Map<String, List<TopicPartition>> {
            val assignment = members.associateWith { ArrayList<TopicPartition>() }
            topics.flatMap { it.partitions }.forEachIndexed { index, partition ->
                assignment.getValue(members[index % members.size]) += partition
            }
            return assignment
        }
    }
}
