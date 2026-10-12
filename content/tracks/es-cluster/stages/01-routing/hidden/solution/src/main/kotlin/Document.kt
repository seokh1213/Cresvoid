class Document(
    val id: Int,
    val brand: String,
    val price: Int,
    val internalNumber: Int,
    titleTokens: List<String>,
) {
    val length: Int = titleTokens.size

    val termFrequencies: Map<String, Int> = titleTokens.groupingBy { it }.eachCount()
}
