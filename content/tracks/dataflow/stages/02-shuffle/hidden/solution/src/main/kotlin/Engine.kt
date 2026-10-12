private const val PARTITION_LIMIT = 1000L
private const val ROW_LIMIT = 1_000_000_000L

class Engine(private val print: (String) -> Unit) {
    private val dictionary = KeyDictionary()
    private val catalog = Catalog()
    private val settings = Settings()
    private val planner = Planner(catalog)
    private val grouping = KeyGrouping(dictionary)
    private var nextJob = 0

    fun startBlock(command: String): RowBlock = try {
        val tokens = tokensOf(command)
        when (tokens.first()) {
            "DATA" -> startData(tokens)
            "APPEND" -> startAppend(tokens)
            else -> error("${tokens.first()} does not open a row block")
        }
    } catch (failure: CommandFailure) {
        RejectedBlock(failure)
    }

    fun finish(block: RowBlock) {
        block.finish()?.let(print)
    }

    fun execute(command: String) {
        val tokens = tokensOf(command)
        try {
            when (tokens.first()) {
                "PIPE" -> catalog.definePipeline(parsePipe(tokens))
                "PLAN" -> plan(tokens)
                "RUN" -> run(tokens)
                "PROFILE" -> profile(tokens)
                "SET" -> settings.apply(tokens)
                else -> fail(ErrorCode.UNKNOWN_COMMAND)
            }
        } catch (failure: CommandFailure) {
            print(failure.line)
        }
    }

    private fun startData(tokens: List<String>): RowBlock {
        if (tokens.size != 3 || !isName(tokens[1])) syntaxError()
        val partitions = integerIn(tokens[2], 1L..PARTITION_LIMIT) ?: syntaxError()
        if (tokens[1] in catalog) fail(ErrorCode.EXISTS)
        val dataset = Dataset(tokens[1], partitions.toInt())
        return RowLoader(dataset, dictionary) { catalog.addDataset(dataset) }
    }

    private fun startAppend(tokens: List<String>): RowBlock {
        if (tokens.size != 2 || !isName(tokens[1])) syntaxError()
        val dataset = catalog.dataset(tokens[1]) ?: fail(ErrorCode.NOT_FOUND, tokens[1])
        return RowLoader(dataset, dictionary) {}
    }

    private fun plan(tokens: List<String>) {
        if (tokens.size != 2 || !isName(tokens[1])) syntaxError()
        planLines(planner.plan(existing(tokens[1]))).forEach(print)
    }

    private fun run(tokens: List<String>) {
        if (tokens.size !in 2..3 || !isName(tokens[1])) syntaxError()
        val limit = if (tokens.size == 3) integerIn(tokens[2], 0L..ROW_LIMIT) ?: syntaxError() else null
        val outcome = runJob(existing(tokens[1]))
        print(jobHeader(nextJob++, outcome))
        resultLines(outcome.result, limit, dictionary).forEach(print)
    }

    private fun profile(tokens: List<String>) {
        if (tokens.size != 2 || !isName(tokens[1])) syntaxError()
        val outcome = runJob(existing(tokens[1]))
        profileLines(nextJob++, outcome).forEach(print)
    }

    private fun existing(name: String): String = name.also { if (it !in catalog) fail(ErrorCode.NOT_FOUND, it) }

    private fun runJob(name: String): JobOutcome = JobExecution(planner.plan(name), settings, dictionary, grouping).run()
}
