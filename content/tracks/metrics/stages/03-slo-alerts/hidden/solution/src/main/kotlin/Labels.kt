data class LabelPair(val name: String, val value: String)

data class Selector(val metric: String, val labels: Set<LabelPair>)
