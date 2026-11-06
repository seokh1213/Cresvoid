class Hotel {
    private val rooms = mutableMapOf<String, RoomInventory>()
    private val reservations = mutableMapOf<String, Reservation>()
    private val requests = RequestHistory()
    private var batch: Batch? = null

    fun room(type: String): RoomInventory? = rooms[type]
    fun hasReservation(id: String): Boolean = id in reservations

    fun execute(command: Command): String = when (command) {
        is Command.Begin -> begin(command.mode)
        is Command.BatchOperation -> advanceBatch(command)
        is Command.HotelOperation -> if (batch != null) "ERROR BATCH_ACTIVE" else executeHotel(command)
    }

    private fun begin(mode: LockMode): String {
        if (batch != null) return "ERROR BATCH_ACTIVE"
        batch = Batch(mode, this)
        return "OK BEGIN $mode"
    }

    private fun advanceBatch(command: Command.BatchOperation): String {
        val active = batch ?: return "ERROR NO_BATCH"
        val response = active.execute(command)
        if (command == Command.End && active.finished) batch = null
        return response
    }

    private fun executeHotel(command: Command.HotelOperation): String = when (command) {
        is Command.Type -> registerType(command)
        is Command.Stock -> setStock(command)
        is Command.Avail -> availability(command)
        is Command.Book -> book(command.booking)
        is Command.Cancel -> cancel(command.cancellation)
        is Command.Status -> reservations[command.id]?.status() ?: "ERROR RESERVATION_NOT_FOUND"
        is Command.Request -> requests.execute(command, ::executeHotel)
    }

    private fun registerType(command: Command.Type): String {
        if (command.type in rooms) return "ERROR TYPE_EXISTS"
        rooms[command.type] = RoomInventory(command.percent)
        return "OK TYPE ${command.type}"
    }

    private fun setStock(command: Command.Stock): String {
        val room = rooms[command.type] ?: return "ERROR TYPE_NOT_FOUND"
        if (!room.setStock(command.stay, command.base)) return "ERROR BELOW_RESERVED"
        return "OK STOCK ${command.type}"
    }

    private fun availability(command: Command.Avail): String {
        val room = rooms[command.type] ?: return "ERROR TYPE_NOT_FOUND"
        val stock = room.snapshot(command.date)
        return "STOCK ${command.type} ${command.date} ${stock.base} ${stock.limit} ${stock.reserved} ${stock.remaining}"
    }

    private fun book(booking: Booking): String {
        val room = rooms[booking.type] ?: return "ERROR TYPE_NOT_FOUND"
        if (hasReservation(booking.id)) return "ERROR RESERVATION_EXISTS"
        if (!room.canBook(booking)) return "ERROR FULL"
        confirm(booking)
        return "OK BOOK ${booking.id}"
    }

    fun confirm(booking: Booking) {
        rooms.getValue(booking.type).adjust(booking.stay, booking.quantity)
        reservations[booking.id] = Reservation(booking)
    }

    private fun cancel(cancellation: Cancellation): String {
        val reservation = reservations[cancellation.id] ?: return "ERROR RESERVATION_NOT_FOUND"
        if (!reservation.booking.stay.contains(cancellation.stay)) return "ERROR PERIOD"
        if (!reservation.canCancel(cancellation)) return "ERROR CANCEL_QUANTITY"
        reservation.cancel(cancellation)
        rooms.getValue(reservation.booking.type).adjust(cancellation.stay, -cancellation.quantity)
        return "OK CANCEL ${cancellation.id}"
    }
}
