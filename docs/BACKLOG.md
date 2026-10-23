# Backlog de Mica TB (issues para el agente)

Orden recomendado. Una issue por sesión. Cada una indica objetivo, alcance, dependencias y criterio de aceptación. Al terminar cada issue: actualizar PROGRESS.md y hacer commit.

## Fase 0

### MT-01 Inspección del modelo y contrato
- Objetivo: saber qué hace el .tflite y dejar la referencia en Python.
- Alcance: inspeccionar tensores (forma, tipo, rango, cuantización), completar models/model_config.json (sección 4 del contrato), escribir reference/reference_infer.py que corre el .tflite con el preprocesado y postprocesado de la sección 5, generar reference/test_vectors/ con expected.json, escribir el hash sha256 de los modelos.
- Dependencias: archivos .tflite, data.yaml, imágenes de muestra.
- Aceptación: el script corre de extremo a extremo, el conteo coincide con lo esperable en las imágenes de muestra y todos los campos VERIFICAR de la configuración están resueltos o documentados en docs/DECISIONS.md.

### MT-02 Proyecto Android base
- Objetivo: app que abre en el celular real.
- Alcance: proyecto Kotlin con Compose y Material 3, Navigation Compose, catálogo de versiones de Gradle, assets con los modelos y la configuración, manifiesto sin permiso INTERNET, comandos documentados en CLAUDE.md.
- Dependencias: Android Studio, depuración USB, MT-01 para los assets.
- Aceptación: ./gradlew assembleDebug y adb install funcionan y la app abre mostrando el id del modelo leído de la configuración.

## Fase 1: cámara e IA (prioridad máxima)

### MT-03 Preprocesado y matemática del letterbox
- Alcance: función pura de letterbox (escala, desplazamientos y su inverso), conversión a tensor según la configuración.
- Dependencias: MT-02.
- Aceptación: pruebas JVM con casos de imágenes anchas, altas y cuadradas, e ida y vuelta de coordenadas con error menor que 1 píxel.

### MT-04 Detector: carga, decodificación, NMS y conteo, con paridad
- Alcance: clase Detector con LiteRT en CPU, decodificación del tensor de salida, NMS, filtrado por conf sobre detecciones crudas, conteos estricto y amplio, tiempos por etapa. Prueba instrumentada de paridad con reference/test_vectors.
- Dependencias: MT-01, MT-03.
- Aceptación: cumple la compuerta de paridad de la sección 6 del contrato (mismo conteo, IoU mayor o igual a 0,95, diferencia de puntaje menor o igual a 0,01) y la salida de connectedDebugAndroidTest se pega como evidencia.

### MT-05 Pantalla de imagen fija
- Alcance: elegir una imagen de la galería, ejecutar el detector, dibujar cajas y mostrar conteos, conf ajustable y tiempos.
- Dependencias: MT-04.
- Aceptación: con las imágenes de prueba, las cajas coinciden visualmente con las de Python y el deslizador cambia el conteo sin volver a ejecutar el modelo. Esta pantalla es también el plan B de la demo.

### MT-06 Cámara con CameraX
- Alcance: vista previa, captura a resolución máxima, corrección EXIF, tocar para enfocar, bloqueo de exposición, balance de blancos y enfoque con Camera2Interop, guardado de la foto original.
- Dependencias: MT-02.
- Aceptación: 20 capturas seguidas sin fallo, exposición constante en los metadatos y orientación correcta.

### MT-07 Analizador de calidad
- Alcance: varianza del Laplaciano en gris reducido, brillo medio, fracción saturada, umbrales en configuración, pantalla de calibración de nitidez. Sin OpenCV.
- Dependencias: MT-02.
- Aceptación: pruebas JVM con imágenes sintéticas desenfocadas (desenfoque gaussiano creciente) que puntúan menos de forma monótona, y registro de puntajes en el celular.

### MT-08 Pantalla integrada de captura y análisis
- Alcance: cámara, calidad, detección, superposición de cajas, conteos, conf, interruptor de conteo, indicador de calidad, guardado de la foto original y del JSON del resultado.
- Dependencias: MT-04, MT-06, MT-07.
- Aceptación: prueba de extremo a extremo en el celular (foto, calidad, detección, cajas, conteo, archivos guardados). Es el primer prototipo que usa la cámara con la IA.

### MT-09 Benchmark y ajustes técnicos
- Alcance: pantalla de ajustes con información del modelo y botón de benchmark (sección 7 del contrato), con exportación del informe a JSON.
- Dependencias: MT-04.
- Aceptación: informe con mediana y p95 por etapa para FP16 e INT8 en CPU, anotado en PROGRESS.md con el modelo del celular.

## Fase 2: sesión de varios campos

### MT-10 Persistencia con Room
- Alcance: tablas sessions, fields, captures e inferences (sección 7 de SPEC.md), repositorio, escritura de archivo primero y fila después.
- Dependencias: MT-08.
- Aceptación: pruebas de DAO y del repositorio, y verificación de que no quedan archivos huérfanos tras un fallo simulado.

### MT-11 Sesión manual, resultados e historial
- Alcance: ManualMotionController, pantalla de sesión con progreso, pantalla de resultados con galería y resumen, historial.
- Dependencias: MT-10.
- Aceptación: sesión de 10 campos, cerrar y reabrir la app, resultados y fotos intactos.

### MT-12 Gradación de demostración
- Alcance: función pura con la tabla de la escala en un archivo de configuración, exigencia de campos mínimos, rótulo "gradación de demostración, no diagnóstica".
- Dependencias: MT-11. Validar la tabla con la persona especializada del equipo.
- Aceptación: pruebas unitarias con casos conocidos en cada rango y con campos insuficientes.

## Fase 3: barrido automatizado

### MT-13 Orquestador, estados, serpentino y simulador
- Alcance: ScanOrchestrator con la máquina de estados, generateSnakePath con solapamiento, SimulatedMotionController con retardos y fallos, pausa, reanudación y cancelación.
- Dependencias: MT-11.
- Aceptación: pruebas JVM del serpentino y de todas las transiciones, barrido simulado de 50 campos con fallos inyectados, sin campos faltantes ni duplicados.

### MT-14 Recuperación de sesiones interrumpidas
- Alcance: detectar sesiones RUNNING al iniciar, ofrecer reanudar desde el último campo completado.
- Dependencias: MT-13.
- Aceptación: matar la app a mitad de un barrido simulado y reanudar sin perder ni duplicar campos.

## Fase 4: hardware

### MT-15 BLE y simulador de ESP32
- Alcance: BleMotionController con el protocolo de la sección 5 de SPEC.md, simulador del controlador con inyección de pérdida de ACK, DONE tardío, duplicados y desconexión, STOP prioritario, vigilancia por PING.
- Dependencias: MT-13 y el acuerdo del protocolo con el responsable del firmware.
- Aceptación: pruebas con el simulador de todos los casos, y luego con el ESP32 real: movimiento confirmado, reintentos sin duplicar y detención segura al desconectar.

## Fase 5: integración

### MT-16 Calibración e integración con el microscopio
- Alcance: pantalla o parámetros para campo de visión, solapamiento, tiempo de estabilización y umbral de nitidez, barrido físico real.
- Dependencias: MT-15 (o modo manual) y el adaptador óptico.
- Aceptación: barrido físico completo con todos los campos válidos o marcados, sin fotos faltantes ni duplicadas, y 20 a 30 campos reales examinados con el pase cualitativo.

### MT-17 APK de demostración
- Alcance: APK instalable, lista de verificación de la demo (batería, brillo, modo avión, orden de láminas), plan B con imágenes fijas de la pantalla de MT-05.
- Dependencias: MT-16.
- Aceptación: la demo completa funciona en modo avión en el celular elegido.

## Fase 6: posterior (no hacer antes de tiempo)

### MT-18 Mejoras
- Candidatos: modelo de TB, mosaicos a escala nativa si el pase con fotos reales lo pide, deduplicación en campos solapados, PDF, mapa de campos, GPU, autofoco motorizado.
- Se convierte en issues separadas cuando se decida cada una.
