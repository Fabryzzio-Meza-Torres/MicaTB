# Progreso

Estado vivo del trabajo. Última actualización: 2026-10-30.

## Rediseño Stitch (2026-10-30, rama feature/stitch-redesign)

La interfaz sigue ahora los diseños de Stitch (tema claro, Space Grotesk, Manrope y JetBrains Mono, barra de 5 pestañas: Inicio, Escaneos, Galería, Informes, Ajustes). Solo cambió la capa de UI: detector, orquestador, base de datos y contrato del modelo no se tocaron. Detalle y lo que se omitió a propósito: DESIGN.md.

Verificado: 58 de 58 pruebas JVM; las cinco pestañas, el detalle de informe, el detalle de campo y la captura vistos en el emulador con datos reales (conteos y cajas del modelo). Sin verificar: celular real, vista previa de cámara (negra en el emulador, como antes), orientación en pantallas pequeñas.

## Resumen

La aplicación completa corre de punta a punta en el emulador (Android 15, x86_64): captura con cámara o imagen, calidad, detección, conteo, sesiones de varios campos con persistencia, resultados, historial, recuperación tras cierre forzado y barrido con movimiento manual, simulado o por protocolo BLE contra un ESP32 simulado.

Lo que falta depende de hardware o de personas, no de código: ver "Pendiente fuera del código".

## Estado por issue

| Issue | Estado | Evidencia |
|---|---|---|
| MT-01 contrato del modelo | Hecho | models/model_config.json, reference/reference_infer.py, 23 vectores de prueba |
| MT-02 proyecto base | Hecho | compila, sin permiso INTERNET (manifiesto fusionado y APK) |
| MT-03 letterbox y preprocesado | Hecho | LetterboxTest, PythonParityTest (píxel a píxel contra Python) |
| MT-04 detector y paridad | Hecho | DetectorParityTest en emulador: 23 de 23, IoU mínimo 0,997, diferencia de puntaje 0,00005 |
| MT-05 imagen fija | Hecho | pantalla Captura con muestras y galería; el deslizador cambia el conteo sin reejecutar el modelo |
| MT-06 cámara | Hecho en emulador | captura, rotación vertical correcta y bloqueo de exposición y balance de blancos; 20 capturas seguidas y orientación en celular real sin probar |
| MT-07 calidad | Hecho | QualityAnalyzerTest (desenfoque monótono), calibración en Ajustes; umbrales iniciales medidos con imágenes públicas |
| MT-08 pantalla integrada | Hecho | captura en emulador, JSON y foto original guardados |
| MT-09 benchmark | Hecho el mecanismo | informe JSON con mediana y p95; cifras de celular pendientes |
| MT-10 Room | Hecho | SessionRepositoryTest (7 pruebas) incluye persistencia al reabrir y huérfanos tras fallo simulado |
| MT-11 sesión, resultados, historial | Hecho | sesión de 10 campos en emulador, galería con cajas, detalle con foto original |
| MT-12 gradación | Hecho | GradingTest; tabla pendiente de validar con la persona especializada |
| MT-13 orquestador y simulador | Hecho | ScanOrchestratorTest (15 pruebas), incluido un barrido de 50 campos con fallos inyectados sin campos perdidos ni repetidos |
| MT-14 recuperación | Hecho | cierre forzado a 6 de 10 campos, reanudación: 1 sesión completa, 10 campos únicos, 10 capturas, sin duplicados |
| MT-15 BLE y simulador | Protocolo y simulador hechos | BleMotionControllerTest (12 pruebas); el transporte GATT real falta |
| MT-16 calibración con microscopio | Pendiente | requiere el equipo |
| MT-17 APK de demostración | APK de depuración listo | lista de verificación abajo; la demo en celular real sin probar |
| MT-18 mejoras | No iniciado | por diseño |

## Verificación reproducible

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest            # 58 pruebas JVM
.\gradlew.bat connectedDebugAndroidTest    # 8 pruebas en el emulador o celular (paridad con el modelo real y base de datos)
.\gradlew.bat assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
```

Última corrida: 58 de 58 pruebas JVM y 8 de 8 instrumentadas en verde.

Emulador usado: AVD micatb (Pixel 6, Android 35, x86_64, 2 GB de RAM, cámara trasera de escena virtual).

## Qué se midió en el emulador y qué no vale

- Los tiempos del benchmark en el emulador (por ejemplo, preproceso de 37 ms e inferencia de 51 ms en una corrida) no son latencia de celular. La latencia real se mide en el celular y se anota aquí.
- La vista previa de la cámara salió negra en reposo con el emulador sin ventana, pero la captura sí funcionó. Se trata como limitación del emulador hasta verlo en un celular.

## Pendiente fuera del código

1. Celular de pruebas: modelo y versión de Android sin definir. Con él: instalar el APK, repetir la paridad con ARM (`connectedDebugAndroidTest`), medir latencia (Ajustes > Medir latencia), 20 capturas seguidas y orientación EXIF.
2. Microscopio y adaptador: calibrar la nitidez (Ajustes > Calidad de imagen) y el ancho del campo para la barra de escala; fijar el tiempo de estabilización; pase cualitativo con 20 a 30 campos reales.
3. Firmware ESP32: acordar el UUID del servicio GATT y el protocolo de SPEC sección 5. Falta escribir el transporte GATT sobre la interfaz BleTransport; no se hizo sin hardware para probarlo.
4. Tabla de gradación: validarla con la persona especializada del equipo antes de mostrarla.
5. Modelo de TB: entra cambiando models/model_config.json (clases, conteo, umbrales y aviso visible).
6. Decisión sobre INT8 contra FP16: se toma con el benchmark en el celular.

## Lista de verificación de la demo (MT-17)

- Batería cargada y brillo bajo, modo avión activo (la app no tiene permiso de Internet).
- Instalar el APK de depuración: `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
- Plan B sin microscopio: Sesión > Imágenes > Muestras, o Captura > Imagen.
- Orden sugerido: Inicio, Captura con una muestra densa, una sesión de 10 campos, Resultados, detalle de un campo con la foto original.
- Decir en voz alta que es apoyo al tamizaje, no diagnóstico, y que el modelo es de E. coli.