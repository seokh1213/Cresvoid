class Nodes {
    var settings: Settings? = null
        private set
    private val issuers = mutableMapOf<String, IdIssuer>()
    private val machines = mutableSetOf<Long>()

    fun execute(tokens: List<String>): String {
        if (tokens[0] == "CONFIG") return configure(tokens)
        val current = settings ?: return "ERR UNCONFIGURED"
        return when (tokens[0]) {
            "NODE" -> register(current.layout, tokens[1], tokens[2].toLong())
            "NEXT" -> {
                val issuer = issuers[tokens[1]] ?: return "ERR NODE"
                issuer.next(current, tokens.drop(3).map(String::toLong)).render()
            }
            "DECODE" -> {
                val id = tokens[1].toLong()
                if (current.layout.containsId(id)) current.layout.decode(id).render() else "ERR ID"
            }
            else -> error("Unknown node command")
        }
    }

    private fun configure(tokens: List<String>): String {
        val replacement = Settings.parse(tokens) ?: return "ERR CONFIG"
        settings = replacement
        issuers.clear()
        machines.clear()
        return "OK"
    }

    private fun register(layout: Layout, name: String, machine: Long): String {
        if (!layout.containsMachine(machine)) return "ERR MACHINE"
        if (name in issuers || machine in machines) return "ERR EXISTS"
        issuers[name] = IdIssuer(machine)
        machines.add(machine)
        return "OK"
    }
}
