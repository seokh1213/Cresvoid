private const val SAFE_MODE_EXTENSION = 30L
private const val THRESHOLD_PER_MILLE = 999L

sealed interface SafeModeState {
    data object Off : SafeModeState

    data object AwaitingReports : SafeModeState

    data class Extending(val exitAt: Long) : SafeModeState
}

class SafeMode {
    var state: SafeModeState = SafeModeState.Off
        private set

    val isOn: Boolean get() = state != SafeModeState.Off

    fun enter() {
        state = SafeModeState.AwaitingReports
    }

    fun checkThreshold(now: Long, health: BlockHealth) {
        if (state == SafeModeState.AwaitingReports && health.safeBlocks >= requiredSafeBlocks(health.blocks)) {
            state = SafeModeState.Extending(exitAt = now + SAFE_MODE_EXTENSION)
        }
    }

    fun exitsAt(now: Long): Boolean = (state as? SafeModeState.Extending)?.exitAt == now

    fun leave() {
        state = SafeModeState.Off
    }
}

fun requiredSafeBlocks(totalBlocks: Int): Int = ((totalBlocks * THRESHOLD_PER_MILLE + 999) / 1000).toInt()
