# Sistema visual de Mica TB

Documento del mundo visual tal como quedó construido (app/src/main/java/pe/utec/micatb/ui/theme y ui/components). Lo que dice PRODUCT.md manda sobre esto.

## Mundo: lámina de micrografía

El campo microscópico es una lámina de figura: la imagen manda, y cada número cuelga de ella con una llamada numerada y una leyenda. La interfaz es neutra (hueso sobre negro mate de lámina) y el color se reserva para los datos, con los pigmentos de la tinción Gram: safranina para Gram negativo y violeta cristal para Gram positivo.

Escena que decide el tema: un cuarto con poca luz, el celular en vertical sobre el ocular, guantes. Por eso el esquema oscuro es el principal; el claro ("hoja de contacto", hueso con tinta) existe para revisar resultados con luz ambiente y sigue el ajuste del sistema.

Dirección elegida por el usuario sobre la asignada por el sorteo; alternativas descartadas: registro de exposición de cuarto oscuro, edición de margen ancho, mosaico denso japonés y el estándar Material 3 clínico.

## Color

| Rol | Oscuro | Claro |
|---|---|---|
| Fondo y superficie | #0E0F10 | #EDE8DB |
| Texto | #DDD8CB | #14161A |
| Texto secundario | #A7A398 | #4A4C50 |
| Acción primaria | hueso #E8E3D6 sobre #0E0F10 | tinta #14161A sobre #EDE8DB |
| Filete | #34383C | #BDB7A8 |
| Gram negativo (datos) | #E5589A, suave #F2A3C6 | #B0206A, suave #D27AA6 |
| Gram positivo (datos) | #9F82F0, suave #C6B6F7 | #6445C4, suave #9C86DE |
| Advertencia | #E2B33F | #8A6200 |
| Correcto | #8FC4A2 | #2F7A4B |
| Error | #F2735C | #B3261E |

Reglas: el color de acción nunca es un color de datos; la safranina solo aparece en cajas, llamadas y glifos de clase. Una clase nunca se distingue solo por color: la forma dice la morfología (rectángulo = bacilo, elipse = coco), el trazo dice el Gram (continuo = negativo, discontinuo = positivo) y el color lo refuerza. Los estados usan icono y texto además del color.

## Tipografía

Barlow (licencia OFL, incluida en la app para funcionar sin red) en Regular, Medium y SemiBold, y Barlow Condensed Medium para rótulos de instrumento en mayúsculas. Una sola familia para titulares, cuerpo, botones y datos; escala de pasos cortos (Operate), nada por debajo de 12 sp y los datos en 15 sp o más. Conteos, umbrales, tiempos y huellas usan cifras tabulares para que no bailen al cambiar.

| Rol | Tamaño | Uso |
|---|---|---|
| displaySmall | 36 sp SemiBold | conteo de un campo |
| headlineMedium / Small | 28 / 24 sp SemiBold | título de pantalla |
| titleLarge / Medium / Small | 22 / 17 / 15 sp Medium | secciones y filas |
| bodyLarge / Medium / Small | 17 / 15 / 13 sp | texto |
| labelLarge | 16 sp SemiBold | botones |
| labelMedium | 14 sp Medium | etiquetas de navegación |
| labelSmall | 12 sp Barlow Condensed, 0,9 sp de tracking | rótulos de sección en mayúsculas |

## Forma, elevación y movimiento

Esquinas pequeñas (2, 4, 6, 8 y 12 dp); los botones y selectores usan la forma de 4 dp, no la píldora por defecto. Sin sombras: la jerarquía sale de capas tonales y filetes de 1 dp. Movimiento solo para comunicar estado (anillo de enfoque, barra de progreso); nada decorativo ni coreografía de entrada.

## Componentes (ui/components)

- DisclaimerStrip: aviso permanente "Apoyo al tamizaje, no diagnóstico" y el aviso del modelo activo (viene de model_config.json). Nunca se oculta.
- PlateFrame: marco de lámina, negro con un filete fino.
- Legend y Reading: rótulo condensado en mayúsculas y par rótulo-valor con cifras tabulares.
- ShutterButton: único control primario de la captura; anillo con núcleo, 80 dp.
- SideAction, MicaButton, MicaOutlinedButton, Segmented: área táctil de 48 a 56 dp.
- ClassGlyph y DetectionOverlay: glifos y cajas de clase, con llamadas numeradas solo en lo contado; lo no contado queda como filete tenue.
- CameraPreviewView: vista previa con enfoque táctil que se mantiene.
- MicaIcons: iconos propios con trazo único de 1,75 en cuadrícula de 24.

## Pantallas

Navegación Material (barra inferior de 5 destinos), edge-to-edge con insets, orientación vertical fija.

1. Inicio: acciones, últimas sesiones, aviso de sesión sin terminar y modelo activo con su huella.
2. Captura: campo, leyenda de conteo con umbral, estricto o amplio, calidad de imagen y tiempos por etapa.
3. Sesión: configuración, progreso con indicación al operador, pausa con problema, cancelación con confirmación y resumen.
4. Resultados: historial, resumen con gradación de demostración, galería con cajas y detalle de campo con la foto original.
5. Ajustes técnicos: modelo y variante, benchmark, calibración de calidad, escala del campo y privacidad.

## Texto

Español, directo, sin tecnicismos innecesarios; los controles nombran su acción y los errores dicen qué pasó y qué hacer. Lo que es demostración se rotula como tal (gradación, modelo de E. coli) y lo que no se midió no se afirma: sin calibración de microscopio no hay barra de escala ni se presentan umbrales de calidad como calibrados.

## No se hizo, a propósito

- Barra de escala sin dato de calibración (se activa al introducir el ancho del campo en Ajustes).
- Cifras de latencia: las del emulador no valen como latencia de celular.
- Capturas de pantalla de referencia de un celular real: ninguna disponible todavía.
