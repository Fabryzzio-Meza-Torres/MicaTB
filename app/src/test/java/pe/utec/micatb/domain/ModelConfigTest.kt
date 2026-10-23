package pe.utec.micatb.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelConfigTest {
    // Lee el model_config.json real de models/ (el mismo que se empaqueta como asset).
    private val config = ModelConfig.parse(File("../models/model_config.json").readText())

    @Test fun parsesModelIdAndClasses() {
        assertEquals("ecoli_gb_E3s43_352_fp16", config.modelId)
        assertEquals(listOf("G-cocci", "G+cocci", "G-bacilli", "G+bacilli"), config.classes)
    }

    @Test fun parsesCountingRules() {
        assertEquals(listOf("G-bacilli"), config.strict.classes)
        assertEquals(0.45f, config.strict.conf, 0f)
        assertEquals(listOf("G-bacilli", "G+bacilli"), config.broad.classes)
        assertEquals(0.40f, config.broad.conf, 0f)
    }
}