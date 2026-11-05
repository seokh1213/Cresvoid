data class Product(val id: String, val name: String, val price: Long, val stock: Int) {
    fun text(): String = "id=$id\nname=$name\nprice=$price\nstock=$stock"
}

object Catalog {
    val products = listOf(
        Product("p-100", "머그 \"밤\"", 12000, 8),
        Product("p-200", "드립 필터", 4500, 0),
        Product("p-300", "원두\n중배전", 18000, 5),
        Product("p-400", "서버\\유리", 24000, 3),
        Product("p-500", "계량 스푼", 7000, 20),
    )
    val byId = products.associateBy { it.id }
}
