package pe.utec.micatb.scan

import pe.utec.micatb.data.FieldPos

/**
 * Recorrido en serpentin: la primera fila va de izquierda a derecha, la siguiente de derecha a izquierda,
 * y asi, para que entre filas el desplazamiento sea de un solo paso. Posiciones en pasos del motor.
 */
fun generateSnakePath(cols: Int, rows: Int, stepX: Int, stepY: Int, originX: Int = 0, originY: Int = 0): List<FieldPos> {
    require(cols > 0 && rows > 0) { "la cuadricula necesita al menos 1 columna y 1 fila" }
    val out = ArrayList<FieldPos>(cols * rows)
    for (r in 0 until rows) {
        val columns = if (r % 2 == 0) 0 until cols else (cols - 1) downTo 0
        for (c in columns) out += FieldPos(out.size, originX + c * stepX, originY + r * stepY)
    }
    return out
}
