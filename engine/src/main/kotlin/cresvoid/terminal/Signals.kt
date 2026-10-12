package cresvoid.terminal

import sun.misc.Signal
import sun.misc.SignalHandler

fun handleSignal(name: String, handler: SignalHandler): SignalHandler? =
    try {
        Signal.handle(Signal(name), handler)
    } catch (_: IllegalArgumentException) {
        null
    }

fun restoreSignal(name: String, previous: SignalHandler?) {
    if (previous != null) Signal.handle(Signal(name), previous)
}
