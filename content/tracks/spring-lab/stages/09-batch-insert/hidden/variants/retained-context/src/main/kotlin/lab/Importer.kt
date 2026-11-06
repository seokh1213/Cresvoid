package lab

class Importer {
    fun insert(work: ImportSession, rows: List<InputRow>) {
        rows.forEachIndexed { index, row ->
            work.persist(ImportRow(row.runKey, row.ordinal, row.payload))
            if ((index + 1) % 200 == 0) work.flush()
        }
        work.flush()
    }
}
