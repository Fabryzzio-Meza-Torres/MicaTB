package pe.utec.micatb.ui

import java.util.Locale

private val ES = Locale.forLanguageTag("es")

/** Decimal con coma, como en el resto de los textos del producto (0,45). */
fun Float.fmt(digits: Int = 2): String = String.format(ES, "%.${digits}f", this)

fun Double.fmt(digits: Int = 1): String = String.format(ES, "%.${digits}f", this)

fun Float.pct(digits: Int = 1): String = String.format(ES, "%.${digits}f %%", this * 100)

/** Tiempo en milisegundos, sin decimales por encima de 10 ms. */
fun Double.ms(): String = if (this < 10) String.format(ES, "%.1f ms", this) else String.format(ES, "%.0f ms", this)
