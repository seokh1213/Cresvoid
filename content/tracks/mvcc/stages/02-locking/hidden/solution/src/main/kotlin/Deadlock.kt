fun transactionsInCycleWith(start: Transaction, blockersOf: (Transaction) -> Set<Transaction>): Set<Transaction> {
    val reachable = reachableFrom(blockersOf(start), blockersOf) + start
    val waitersOf = HashMap<Transaction, MutableList<Transaction>>()
    for (waiter in reachable) {
        for (holder in blockersOf(waiter)) waitersOf.getOrPut(holder) { ArrayList() }.add(waiter)
    }
    // start 에서 닿으면서 start 로 돌아오는 트랜잭션이 start 를 지나는 고리 위에 있다
    return reachableFrom(waitersOf[start].orEmpty()) { waitersOf[it].orEmpty() }
}

private fun reachableFrom(starts: Collection<Transaction>, next: (Transaction) -> Collection<Transaction>): Set<Transaction> {
    val found = HashSet<Transaction>()
    val pending = ArrayDeque(starts)
    while (pending.isNotEmpty()) {
        val transaction = pending.removeLast()
        if (found.add(transaction)) pending.addAll(next(transaction))
    }
    return found
}
