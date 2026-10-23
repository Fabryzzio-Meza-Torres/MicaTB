# Mica TB

App Android offline de apoyo al tamizaje microscópico. El celular va acoplado al ocular de un microscopio óptico, captura campos, un detector YOLOv8n en TFLite cuenta bacilos y la app consolida los resultados para que los revise una persona especializada. No es un dispositivo de diagnóstico y la interfaz debe decirlo.

Equipo: UTEC (Fabryzzio en computación, Maritza en bioingeniería, Oswaldo en mecatrónica). Proyecto para una competencia internacional con jurado clínico. La demo usa E. coli en láminas Gram fijadas porque el laboratorio solo maneja organismos de bioseguridad nivel 1. Los resultados de TB se muestran solo con imágenes públicas y la validación con láminas reales está pendiente.

## Prioridad actual

1. Que funcione la rebanada cámara, IA y resultado en el celular real (Fase 1 de docs/SPEC.md). Es lo que más importa.
2. Después: sesión de varios campos, persistencia, resultados e historial.
3. El hardware motorizado (ESP32 por BLE) se desarrolla detrás de una interfaz con simulador. Que falte el hardware no bloquea nada.

## Hechos verificados sobre el modelo (no los inventes ni los cuestiones)

- YOLOv8n de Ultralytics, ajustado desde COCO, unos 3,0 M de parámetros.
- Es un detector de bacterias Gram entrenado con el Clinical Bacteria DataSet. NO es un detector de TB (Ziehl-Neelsen). Sirve para la demo con E. coli. El modelo de TB vendrá después y debe entrar solo cambiando models/model_config.json, sin tocar código.
- Modelo actual (ruta PAD352): entrenado a 320; en inferencia se hace letterbox a 320 y se añaden 16 px de gris 114 por lado, así que la entrada es 352 x 352 (el borde es parte del modelo: sin él baja el recall). TFLite FP16, archivo ecoli_gb_E3s43_352_fp16.tflite (6,46 MB). Variante INT8 (3,4 MB) para medir en el benchmark. La ruta anterior de 320 sin borde y el respaldo de 416 no se usan.
- 4 clases Gram: G-cocci, G+cocci, G-bacilli, G+bacilli. El conteo principal (estricto) usa solo G-bacilli. El conteo amplio suma G-bacilli y G+bacilli.
- Parámetros de operación elegidos en validación: NMS IoU 0,5, max_det 1000, conf 0,45 para el conteo estricto y 0,40 para el amplio.
- Métricas en test (890 imágenes, datos parecidos a los de entrenamiento, no del celular): precisión 0,901, recall 0,874 (IC 95 % 0,828 a 0,919; media de 3 semillas 0,858), error medio de conteo 0,185 por imagen. El recall cumple por poco: no darlo por cerrado. Latencia 22 a 26 ms es solo una estimación en CPU de Kaggle, no una medición en celular.
- Debilidades conocidas: campos densos y células pequeñas (cifras de la ruta 320 anterior, ver docs/ref/MODEL_CARD.md), sensibilidad al brillo, y brecha de dominio entre las imágenes de entrenamiento y las fotos de un celular sobre el ocular.

## Verificado en MT-01 y lo que sigue sin verificar

Verificado inspeccionando los .tflite (ver docs/MODEL_CONTRACT.md y docs/DECISIONS.md): entrada [1,3,352,352] float32 NCHW en RGB 0 a 1, salida [1,8,2541] float32 sin NMS, cajas cx,cy,w,h normalizadas respecto a 352, puntajes ya activados, orden de clases confirmado, cuantización 0 también en INT8. El script reference/reference_infer.py reproduce los resultados de Kaggle (conteos 20 de 20).

Sigue sin verificar: paridad de la app contra reference/ (MT-04), latencia en el celular (MT-09) y comportamiento con fotos reales de microscopio.

## Stack decidido

- Android nativo, Kotlin, minSdk 26. Jetpack Compose con Material 3 y Navigation Compose.
- ViewModel con StateFlow y corrutinas. Inyección manual por constructor (una clase AppContainer). Sin Hilt ni Dagger.
- CameraX para vista previa y captura. Camera2Interop para bloquear exposición, balance de blancos y enfoque.
- LiteRT (antes TensorFlow Lite): artefacto com.google.ai.edge.litert. La documentación vista indica la versión 2.1.0 y que la aceleración por GPU con la API Interpreter es solo de los paquetes V1, mientras que V2 usa CompiledModel. Resuelve esto con la documentación oficial antes de elegir. Empieza con CPU (XNNPACK, 4 hilos). La GPU es un experimento posterior.
- Room (SQLite) para persistencia. Capturas como archivos en el almacenamiento privado de la app.
- BLE: biblioteca Kable (JuulLabs) detrás de la interfaz MotionController, o la API nativa envuelta. Verifica versión y mantenimiento antes de añadirla.
- Nitidez (varianza del Laplaciano) en Kotlin puro. Sin OpenCV.
- La app NO declara el permiso INTERNET en el manifiesto. Así se garantiza que funciona offline.
- Paquete sugerido: pe.utec.micatb (cámbialo si te lo indico).

Por qué nativo y no Flutter: la parte más riesgosa del proyecto es cámara, IA y BLE juntas. Con nativo se controla la cámara (bloqueos de exposición, balance de blancos y enfoque, captura en resolución completa), se usa LiteRT directamente y hay menos capas que depurar. tflite_flutter tuvo su última versión en octubre de 2025 y su página no menciona LiteRT.

## Estructura del repositorio

```text
app/src/main/java/pe/utec/micatb/
  ui/          pantallas Compose y ViewModels
  camera/      CameraSource (CameraX)
  quality/     QualityAnalyzer
  inference/   Detector, preprocesado, decodificación, NMS
  motion/      MotionController y sus implementaciones
  scan/        ScanOrchestrator, serpentino, estados
  data/        Room, repositorio, archivos
  domain/      modelos de datos, gradación, configuración
app/src/main/assets/   modelos y model_config.json
models/        copias de los modelos y model_config.json (fuente de verdad)
reference/     reference_infer.py, test_vectors/, imágenes de muestra
training/      notebooks de Kaggle, scripts, tablas y pesos del entrenamiento (solo referencia)
docs/          SPEC.md, MODEL_CONTRACT.md, BACKLOG.md, DECISIONS.md, ref/
PROGRESS.md    estado vivo del trabajo
```

## Comandos (completa los que falten al terminar la Fase 0)

```bash
adb devices
./gradlew test                        # pruebas unitarias en JVM
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest   # pruebas en el celular (paridad del modelo)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s MicaTB
```

## Reglas de trabajo (IMPORTANTE)

1. Antes de programar una tarea, lee su issue en docs/BACKLOG.md y la sección relevante de docs/SPEC.md. Propón un plan corto y espera mi OK si la tarea toca arquitectura, dependencias, seguridad del movimiento o el contrato del modelo.
2. Una issue por sesión. Al terminar, actualiza PROGRESS.md (qué se hizo, qué falta, cómo probarlo, decisiones) y haz commit.
3. Nunca declares una tarea terminada sin mostrar la salida del comando de prueba o la evidencia en el dispositivo. Que compile no significa que funcione.
4. Ante cualquier duda sobre el modelo, mide y no asumas: inspecciona los tensores y compara contra reference/.
5. Verifica versiones y APIs de librerías con documentación oficial (o con el servidor Context7 si está instalado) antes de añadirlas. No uses versiones de memoria.
6. Cambios pequeños y commits frecuentes, en inglés, con mensajes claros.
7. Toda constante de dominio (umbrales, tamaños, clases, tiempos, tabla de gradación) vive en configuración, nunca dispersa en el código.
8. Las decisiones técnicas relevantes se anotan en docs/DECISIONS.md con fecha, contexto, decisión y alternativas descartadas.
9. La lógica pura (letterbox, decodificación, NMS, serpentino, gradación, máquina de estados, nitidez) lleva prueba unitaria en JVM antes de integrarse.
10. Si algo falla, explica la causa probable y propón la solución más simple. No encadenes parches.

## Qué NO hacer

- No añadir permiso INTERNET, Firebase, analítica ni ninguna dependencia de red.
- No añadir OpenCV, Hilt, multi-módulo ni capas formales de Clean Architecture. Es un MVP.
- No reentrenar ni modificar los modelos. Eso se hace aparte en Kaggle.
- No cambiar el contrato del modelo (docs/MODEL_CONTRACT.md) sin avisarme.
- No tocar docs/ref/ (son resultados del entrenamiento, solo lectura).
- No guardar datos de pacientes. El identificador de muestra es texto libre y local.
- No inventar valores de rendimiento: se miden en el celular y se registran en PROGRESS.md.

## Seguridad del movimiento

- Enviar un comando por BLE no significa que el movimiento terminó. Solo se avanza con confirmación válida del controlador.
- Cancelar envía STOP inmediato. Pausar termina el paso atómico en curso y no inicia el siguiente movimiento.
- Los límites físicos y los finales de carrera los protege el firmware de forma independiente. La app además valida los límites antes de enviar.
- Ante pérdida de conexión, error o timeout: detener, no reintentar movimientos a ciegas y pedir intervención.

## Definición de terminado de una issue

Compila, pasan las pruebas indicadas en la issue, está probado en el celular si toca cámara, IA o interfaz, PROGRESS.md está actualizado y hay commit.

## Estilo de respuesta

Español, breve y concreto. Sin guiones largos ni negritas. Código e identificadores en inglés.

## Documentos (léelos cuando la tarea los necesite)

- docs/SPEC.md: requisitos, arquitectura, máquina de estados, BLE, datos, fases y criterios de aceptación.
- docs/MODEL_CONTRACT.md: qué se sabe del modelo, qué verificar, esquema de model_config.json, paridad y benchmark.
- docs/BACKLOG.md: issues ordenadas con dependencias y criterios de aceptación.
- docs/DECISIONS.md y PROGRESS.md: memoria viva del proyecto.
