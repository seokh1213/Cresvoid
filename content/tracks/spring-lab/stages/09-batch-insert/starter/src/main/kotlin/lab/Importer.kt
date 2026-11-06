package lab

class Importer {
    fun insert(work: ImportSession, rows: List<InputRow>) {
        rows.forEach { row ->
            work.persist(ImportRow(row.runKey, row.ordinal, row.payload))
        }
    }
}
