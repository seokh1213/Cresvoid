private data class HistoryCheckpoint(
    val undo: List<EditCommand>,
    val redo: List<EditCommand>,
)

private class EditHistory {
    private var undoStack = ArrayDeque<EditCommand>()
    private var redoStack = ArrayDeque<EditCommand>()

    fun branchInvalidator(): () -> Unit = { redoStack.clear() }

    fun record(command: EditCommand) {
        undoStack.addLast(command)
    }

    fun takeForUndo(): EditCommand? = undoStack.removeLastOrNull()

    fun takeForReplay(): EditCommand? = redoStack.removeLastOrNull()

    fun retainForReplay(command: EditCommand) {
        redoStack.addLast(command)
    }

    fun checkpoint(): HistoryCheckpoint = HistoryCheckpoint(undoStack.toList(), redoStack.toList())

    fun restore(checkpoint: HistoryCheckpoint) {
        undoStack = ArrayDeque(checkpoint.undo)
        redoStack = ArrayDeque(checkpoint.redo)
    }
}

class EditingSession {
    private val description = ProductDescription()
    private val history = EditHistory()
    private val executor = EditExecutor(description, history.branchInvalidator(), history::record)

    fun execute(request: EditorRequest): String? {
        when (request) {
            is EditorRequest.MoveCursor -> {
                if (!moveCursor(request.position)) return "ERR position"
            }
            is EditorRequest.Write -> write(request.text)
            is EditorRequest.Erase -> {
                if (!erase(request.count)) return "ERR range"
            }
            is EditorRequest.Apply -> return applyEdits(request.requests)
            EditorRequest.Back -> back()
            EditorRequest.Again -> again()
            EditorRequest.View -> return "${description.cursor}|${description.text}"
        }
        return null
    }

    private fun applyEdits(requests: List<EditorRequest>): String? {
        val descriptionCheckpoint = description.checkpoint()
        val historyCheckpoint = history.checkpoint()
        for (request in requests) {
            val error = execute(request) ?: continue
            description.restore(descriptionCheckpoint)
            history.restore(historyCheckpoint)
            return error
        }
        return null
    }

    fun moveCursor(position: Int): Boolean {
        if (!description.containsPosition(position)) return false
        description.moveCursor(position)
        return true
    }

    fun write(text: String) {
        if (text.isEmpty()) return
        executor.executeNew(WriteDescription(description.cursor, text))
    }

    fun erase(count: Int): Boolean {
        if (!description.containsRange(description.cursor, count)) return false
        if (count == 0) return true
        executor.executeNew(EraseDescription(description.cursor, count))
        return true
    }

    fun back() {
        val command = history.takeForUndo() ?: return
        command.undo(description)
        history.retainForReplay(command)
    }

    fun again() {
        val command = history.takeForReplay() ?: return
        executor.replay(command)
    }
}
