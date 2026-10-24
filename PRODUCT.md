# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Usuario principal (confirmado): operador de laboratorio, de bioingeniería o técnico, que acopla el celular al ocular de un microscopio óptico, captura campos, revisa el conteo y cierra la sesión. Trabaja en un cuarto con poca luz, a menudo con guantes, y con el celular siempre en vertical sobre el adaptador del ocular.

Audiencia secundaria: jurado clínico de una competencia internacional, que ve la demo sobre la pantalla del operador, y personal especializado que revisa después las fotos y los resultados de cada sesión.

## Product Purpose

Mica TB es una app Android offline de apoyo al tamizaje microscópico. Captura campos con la cámara del celular, un detector YOLOv8n en TFLite cuenta bacilos en cada campo, y la app consolida los campos de una sesión, guarda las fotos originales y el resultado, y permite revisarlos. No es un dispositivo de diagnóstico y la interfaz lo dice.

Éxito: que la cadena cámara, IA y resultado funcione en el celular real, de forma legible y confiable para quien la opera, y que quien revise después pueda entender qué se contó y con qué calidad de imagen.

## Positioning

Una herramienta de laboratorio que funciona sin red, sobre el microscopio que el laboratorio ya tiene, y que muestra siempre de dónde sale cada número: la foto original, las cajas del detector, el umbral de confianza ajustable y el control de calidad de la imagen. Un competidor genérico de "app de IA para salud" no puede afirmar que cada conteo se puede reabrir y recontar sobre la foto original, sin conexión.

## Operating Context

Equipo UTEC (computación, bioingeniería, mecatrónica). La demo usa E. coli en láminas Gram fijadas, porque el laboratorio solo maneja organismos de bioseguridad nivel 1. El modelo actual detecta bacterias Gram (no es un detector de TB); el modelo de TB vendrá después y debe entrar solo cambiando models/model_config.json. Los resultados de TB solo se muestran con imágenes públicas; la validación con láminas reales está pendiente.

Flujo: montar el celular, enfocar y bloquear exposición y balance de blancos, capturar un campo, ver cajas y conteo, ajustar el umbral si hace falta, mover la lámina, repetir en varios campos, consolidar la sesión. El movimiento es manual primero; el barrido motorizado (ESP32 por BLE) va detrás de una interfaz con simulador y no bloquea nada.

## Capabilities and Constraints

- Android nativo, Kotlin, Jetpack Compose con Material 3, minSdk 26. Sin permiso INTERNET, sin OpenCV, sin Hilt, sin multi-módulo.
- Clases Gram: G-cocci, G+cocci, G-bacilli, G+bacilli. Conteo estricto (solo G-bacilli, conf 0,45) y amplio (G-bacilli y G+bacilli, conf 0,40). El umbral es ajustable sin recapturar ni volver a ejecutar el modelo.
- Avisos obligatorios y visibles: "Apoyo al tamizaje, no diagnóstico" y, mientras el modelo sea el de E. coli, "Modelo de demostración con E. coli". La gradación de la escala se rotula "gradación de demostración, no diagnóstica".
- No se guardan datos de pacientes; el identificador de muestra es texto libre y local.
- Pantallas del MVP: Inicio, Captura y análisis, Sesión, Resultados, Ajustes técnicos.
- Sin decidir: celular de pruebas (modelo y versión de Android), tabla de gradación (se valida con la persona especializada del equipo), UUID y protocolo BLE con el firmware.

## Brand Commitments

Nombre: Mica TB. Sin logo ni paleta obligatoria (confirmado): libertad visual total, salvo los avisos obligatorios de arriba.

## Evidence on Hand

Resultados del entrenamiento en docs/ref/ (MODEL_CARD, ACCEPTANCE, RESUMEN) y 23 imágenes de prueba en reference/test_vectors/ (dataset público Clinical Bacteria DataSet, CC BY 4.0). No hay fotos reales del celular sobre el ocular todavía: no se deben inventar resultados, métricas de rendimiento ni testimonios. Las cifras de latencia se miden en el celular y se registran en PROGRESS.md.

## Product Principles

1. Cada número se puede reabrir: el conteo siempre va acompañado de la foto original, las cajas y el umbral con que se calculó.
2. Honestidad clínica: nada se presenta como diagnóstico; lo que es demostración se rotula como tal, y lo que no se midió no se afirma.
3. Se opera con una mano, con guantes y con poca luz: controles grandes, estados inequívocos y nada que dependa de leer letra pequeña.
4. La imagen manda: la interfaz cede el protagonismo al campo microscópico y no tapa lo que el operador necesita ver.
5. Funciona sin red y falla de forma comprensible: los errores dicen qué pasó y qué hacer.

## Accessibility & Inclusion

Uso con guantes y poca luz: áreas táctiles generosas y alto contraste. Textos de la interfaz en español. No depender solo del color para distinguir clases o estados (ver forma, texto o patrón además del color).
