package pe.utec.micatb.domain

/** Resultado de la gradacion de demostracion. Nunca es diagnostica; [label] lo dice. */
sealed interface Grade {
    data class Graded(val grade: String, val text: String, val meanPerField: Double, val fields: Int, val label: String) : Grade

    /** Hay menos campos validos que el minimo configurado. */
    data class Insufficient(val fields: Int, val needed: Int, val label: String) : Grade
}

object Grading {
    /**
     * Gradua por promedio de bacilos contados por campo valido. La tabla vive en app_config.json.
     * Los limites de cada banda son inclusivos por arriba.
     */
    fun grade(config: GradingConfig, countsPerValidField: List<Int>, minFields: Int): Grade {
        if (countsPerValidField.size < minFields) return Grade.Insufficient(countsPerValidField.size, minFields, config.label)
        val mean = countsPerValidField.average()
        val band = config.bands.firstOrNull { mean <= it.maxMeanPerField } ?: config.bands.last()
        return Grade.Graded(band.grade, band.text, mean, countsPerValidField.size, config.label)
    }
}
