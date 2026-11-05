interface EditCommand {
    fun execute(description: ProductDescription)

    fun undo(description: ProductDescription)
}

class WriteDescription(
    private val position: Int,
    private val insertedText: String,
) : EditCommand {
    private val insertedLength = insertedText.codePointCount(0, insertedText.length)

    override fun execute(description: ProductDescription) {
        description.insert(position, insertedText)
    }

    override fun undo(description: ProductDescription) {
        description.remove(position, insertedLength)
    }
}

class EraseDescription(
    private val position: Int,
    private val count: Int,
) : EditCommand {
    private var removedText: String? = null

    override fun execute(description: ProductDescription) {
        val text = description.read(position, count)
        if (removedText == null) removedText = text
        description.remove(position, count)
    }

    override fun undo(description: ProductDescription) {
        description.insert(position, checkNotNull(removedText))
        description.moveCursor(position)
    }
}

class EditExecutor(
    private val description: ProductDescription,
    private val discardBranch: () -> Unit,
    private val record: (EditCommand) -> Unit,
) {
    fun executeNew(command: EditCommand) {
        discardBranch()
        replay(command)
    }

    fun replay(command: EditCommand) {
        command.execute(description)
        record(command)
    }
}
