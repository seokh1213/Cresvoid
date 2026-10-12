class FollowerProgress(nextIndex: Int) {
    var nextIndex: Int = nextIndex
        private set
    var matchIndex: Int = 0
        private set

    fun recordSuccess(responseIndex: Int) {
        if (responseIndex > matchIndex) matchIndex = responseIndex
        if (responseIndex + 1 > nextIndex) nextIndex = responseIndex + 1
    }

    fun recordRejection(responseIndex: Int) {
        if (responseIndex == nextIndex - 1 && responseIndex > matchIndex) nextIndex = responseIndex
    }
}
