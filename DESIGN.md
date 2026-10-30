# Sistema visual de Mica TB

Documento del mundo visual tal como quedó construido (app/src/main/java/pe/utec/micatb/ui/theme y ui/components). Lo que dice PRODUCT.md manda sobre esto.

## Origen: Stitch "Clinical Diagnostic Precision"

Desde 2026-10-30 la interfaz sigue los diseños de Stitch (carpeta stitch_micatb_ai_microscopy_assistant): instrumento médico claro e higiénico, teal clínico como acción, rojo reservado a los bacilos detectados. Reemplaza al mundo anterior "lámina de micrografía" (oscuro, Barlow). Decisión: piel visual fiel a Stitch y datos reales de la app; lo que Stitch muestra y la app no puede respaldar se omite o se rotula como demostración.

## Color (tema claro único)

| Rol | Valor |
|---|---|
| Fondo y superficie | #F7F9FF |
| Tarjeta | #FFFFFF con filete #E2E8F0 |
| Contenedores | #EDF4FF, #E3EFFF, #D8EAFF |
| Texto | #001D32 (secundario #3D4949) |
| Acción primaria | teal #006768 |
| Chips y estados suaves | #C7DFFF |
| Lavado cian (aviso, modelo) | #E8F7F7, filete #CCECEC |
| Bacilo detectado | #EF4444, lavado #FEE2E2, texto #991B1B |
| Revisión / advertencia | texto #92400E, lavado #FEF3C7 |
| Correcto | texto #047857, lavado #D1FAE5 |

Reglas: el rojo solo marca bacilos contados y sus cajas. Una clase nunca se distingue solo por color: la forma dice la morfología (rectángulo = bacilo, elipse = coco), el trazo dice el Gram (continuo = negativo, discontinuo = positivo). Los estados llevan texto además del color. El tema oscuro se retiró; si hace falta para el cuarto oscuro, se añade aparte.

## Tipografía

Fuentes variables OFL incluidas en la app (funciona sin red): Space Grotesk para titulares y cifras de conteo, Manrope para contenido y botones, JetBrains Mono para identificadores, rótulos y telemetría. Cifras tabulares en conteos, umbrales y tiempos. Licencias en docs/licenses.

## Forma, elevación y movimiento

Radios de 2, 4 (etiquetas), 8 (botones, contenedores) y 12 dp (tarjetas). Sin sombras fuertes: la jerarquía sale de capas tonales y filetes de 1 dp. Movimiento solo para comunicar estado.

## Componentes (ui/components)

- AppHeader: logo, "MicaTB", sección en mono y el chip "100 % offline" (estado real: la app no tiene Internet).
- DisclaimerStrip: franja cian bajo la cabecera, siempre visible: "Apoyo al tamizaje, no diagnóstico" y el aviso del modelo activo (viene de model_config.json).
- MicaCard, MetricBox, Tag, PositiveTag, ReviewTag, CleanTag, StatusChip, LogoMark.
- MicaButton (teal), MicaOutlinedButton (blanco con filete), MicaTonalButton (azul claro), Segmented.
- PlateFrame, ClassGlyph, DetectionOverlay, CameraPreviewView, ShutterButton: se conservan.
- MicaIcons: iconos propios de trazo único.

## Pantallas

Barra inferior de 5 pestañas: Inicio, Escaneos, Galería, Informes, Ajustes. La captura de un campo suelto no tiene pestaña: se abre desde Escaneos y desde Inicio.

1. Inicio: modelo activo, tarjeta de escaneo, métricas del día calculadas de las sesiones guardadas, último tamizaje.
2. Escaneos (Centro de escaneo): pasos Muestra, Enfoque y Barrido, identificador, cámara o muestras, número de campos, movimiento, iniciar barrido. En ejecución conserva progreso, pausa y cancelación.
3. Galería: última sesión, matriz de rastreo, filtros (todos, con bacilos, sin hallazgos, por revisar) y tarjetas de campo con cajas.
4. Informes: búsqueda, última sesión y historial. El PDF aparece deshabilitado ("próximamente").
5. Ajustes: tarjetas de motor de IA, rendimiento, calidad de imagen, escala del campo y operación sin conexión.

## Qué de Stitch se omitió a propósito

- Sincronización en la nube y servidor MINSA: la app no tiene permiso de Internet.
- Sello "MINSA / OMS Validado", nombre y CMP del microscopista: no hay validación ni datos de personas.
- Resultados BAAR y Ziehl-Neelsen: el modelo es de bacilos Gram negativos (E. coli).
- Cifrado AES-256 de la base de datos: no existe, no se afirma.
- PDF, compartir, imprimir y selección múltiple para exportar: no implementados.
- Motor de platina "Calibrado" y datos del ESP32: el transporte BLE real sigue pendiente.

## Texto

Español, directo; los controles nombran su acción y los errores dicen qué pasó y qué hacer. Lo que es demostración se rotula como tal (gradación, modelo de E. coli) y lo que no se midió no se afirma.
