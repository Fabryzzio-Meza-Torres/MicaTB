# Decisiones técnicas

Formato: fecha, contexto, decisión, alternativas descartadas.

## 2026-10-23 Modelo: ruta PAD352 en lugar de 320

Contexto: los documentos del paquete de arranque describían la ruta 320 (NHWC, salida 1x8x2100, conf 0,60). El entregable final del entrenamiento es PAD352.
Decisión: la app usa entrada 352 (letterbox a 320 más 16 px de gris 114 por lado), NCHW, salida [1,8,2541], cajas normalizadas respecto a 352. Verificado inspeccionando los .tflite (docs/MODEL_CONTRACT.md sección 2). CLAUDE.md y MODEL_CONTRACT.md se actualizaron en MT-01.
Descartado: ruta 320 sin borde (el recall de validación baja de 0,866 a 0,818 y A1 y A4 no se cumplían) y respaldo 416 (exportado sin borde, no probado con él).

## 2026-10-23 FP16 por defecto, INT8 como variante a medir

Contexto: deploy_notes.md del entrenamiento recomienda INT8 (3,4 MB); CLAUDE.md pide FP16 por defecto.
Decisión: model_config.json apunta a FP16 y lista INT8 en "variants". En PAD352 el INT8 no pierde mAP50 en la muestra de 300 imágenes (0,902 contra 0,898 de FP32), pero esa muestra es pequeña. Se decide con el benchmark de MT-09 (latencia y conteo en el celular).
Descartado: INT8 por defecto antes de medir en el celular.

## 2026-10-23 Umbral base 0,25 y refiltrado en memoria

Contexto: el deslizador de conf debe cambiar el conteo sin volver a ejecutar el modelo.
Decisión: el modelo se ejecuta con base_conf 0,25, se hace NMS por clase y se guardan las detecciones crudas. Cada definición de conteo filtra después con "puntaje > conf" (estricto 0,45; amplio 0,40). Es equivalente a filtrar antes del NMS porque el NMS voraz por clase solo deja que cajas de mayor puntaje supriman a las de menor. reference_infer.py lo implementa así y reproduce los conteos de Kaggle (que filtraba antes) en 20 de 20 imágenes.
Descartado: ejecutar el modelo con el conf final (obliga a repetir la inferencia al mover el deslizador).

## 2026-10-23 max_det por puntaje global

Contexto: el script original de Kaggle truncaba a max_det en el orden de salida del NMS, que está agrupado por clase.
Decisión: la referencia ordena por puntaje global antes de truncar, como ultralytics. Con menos de 1000 detecciones no cambia nada.

## 2026-10-23 OpenCV solo en la referencia de Python

Decisión: reference_infer.py usa opencv-python para leer y redimensionar. La app no usa OpenCV (regla de CLAUDE.md). Ojo con la paridad: cv2.INTER_LINEAR y el redimensionado bilineal de Android pueden diferir en subpíxeles. Se mide en MT-03 y MT-04.

## 2026-10-23 Repositorio y archivos grandes

Contexto: el repositorio vive en C:\Users\ASUS\OneDrive\Documentos\MicaTB y trae modelos y pesos.
Decisión: *.tflite, *.pt y *.onnx van por Git LFS (.gitattributes). Los notebooks de Kaggle se importan sin salidas (kaggle kernels pull no las trae) más una copia ejecutada de ecoli-final. No se migra la historia git del entrenamiento (rama ecoli-yolo-fase1-training del repositorio de trabajo, 653 MB, sin subir).
Riesgo abierto: Gradle dentro de OneDrive. Si la sincronización bloquea build/ o .gradle/, pausarla durante las compilaciones o mover el proyecto a una ruta fuera de OneDrive.

## 2026-10-23 Tolerancia de puntaje en la paridad (CERRADA)

Contexto: MODEL_CONTRACT.md sección 6 exige diferencia de puntaje menor o igual a 0,01. En MT-01, reference_infer.py (Windows) difería hasta 0,024 de los resultados que Kaggle generó con el mismo FP16.
Resultado: la prueba instrumentada de la app (DetectorParityTest, LiteRT 2.3.0, emulador x86_64 Android 35) contra expected.json de reference_infer.py da, en las 23 imágenes, conteo estricto y amplio idénticos, IoU mínimo 0,997 y diferencia máxima de puntaje 0,00005. La diferencia de 0,024 era entre el intérprete de Kaggle y el de escritorio, no entre la app y la referencia.
Decisión: el criterio del contrato (IoU mayor o igual a 0,95, puntaje dentro de 0,01) se mantiene sin cambios y se cumple. Falta repetirlo en un celular real con ARM.
## 2026-10-23 Toolchain Android (MT-02)

Contexto: versiones verificadas en la documentación oficial el mismo día (developer.android.com y kotlinlang.org), no de memoria.
Decisión: AGP 9.4.0 con Gradle 9.6.0 (mínimo exigido por esa versión; distribución verificada con su sha256 en gradle-wrapper.properties). AGP 9 trae Kotlin integrado, así que no se aplica kotlin-android; solo el plugin de Compose (versión 2.4.21, la estable actual de Kotlin). Compose BOM 2026.09.00, Navigation Compose 2.10.2, Activity Compose 1.13.0. compileSdk 37 porque las dependencias actuales de Compose y lifecycle lo exigen (con 36 el build falla); targetSdk 36 y minSdk 26 se mantienen, son decisiones separadas.
JDK: el que trae Android Studio (JBR 25) vía JAVA_HOME; el build funcionó con él. Android Studio, command-line tools (sha256 verificado) y SDK (platform-tools, plataformas 36 y 37.0, build-tools 36.0.0) se instalaron con winget y sdkmanager, con las licencias aceptadas.
Assets: app/build.gradle.kts añade ../models como directorio de assets, así models/ sigue siendo la única fuente del modelo y de model_config.json. noCompress para tflite. El APK de depuración incluye FP16, INT8 y la configuración (21,8 MB).
Parseo de la configuración: org.json de Android en la app y la librería org.json:json solo en pruebas JVM (las pruebas leen el model_config.json real de models/).
Descartado: aplicar kotlin-android (según la guía oficial, el Kotlin integrado de AGP 9 lo reemplaza) y un JDK 17 aparte (el build funcionó con el JBR).
## 2026-10-24 Umbrales iniciales de calidad de imagen

Contexto: con nitidez minima 40 y brillo maximo 215, la sesion de prueba marco "calidad insuficiente" en 15 de las 23 imagenes buenas del conjunto publico (nitidez 19 a 62, brillo 142 a 216).
Medicion (mismo algoritmo que QualityAnalyzer, 23 imagenes de reference/test_vectors): originales con nitidez entre 19,5 y 62,3; con desenfoque gaussiano sigma=1, entre 8,8 y 17,4; con sigma=2, entre 5,3 y 10,9.
Decision: nitidez minima 15 (0 de 23 originales rechazadas; detecta 18 de 23 con sigma=1 y 23 de 23 con sigma=2) y brillo maximo 235. Siguen marcados como no calibrados: salen de imagenes publicas, no de fotos del celular sobre el ocular. La calibracion con el microscopio real esta en Ajustes > Calidad de imagen.
Descartado: dejar 40 (rechaza imagenes buenas) y un umbral solo por brillo.
## 2026-10-24 Persistencia, API de LiteRT y librerias (MT-04 a MT-15)

LiteRT 2.3.0: el Interpreter de CPU vive en org.tensorflow.lite.Interpreter (paquete de compatibilidad), no en com.google.ai.edge.litert; CompiledModel esta en litert-api. Se usa el Interpreter con 4 hilos y XNNPACK por defecto. La paridad con el .tflite real pasa en el emulador. GPU queda como experimento posterior (RF27).
Room 2.8.5 con KSP 2.3.12 funciona bajo AGP 9.4 y Kotlin integrado. Esquema de docs/SPEC.md seccion 7, sin exportar el esquema. Integridad: foto primero (temporal y renombrar), fila despues en una transaccion; si falla, se borra la foto.
CameraX 1.6.2 con Camera2Interop para bloquear exposicion y balance de blancos; el enfoque se fija con enfoque tactil sin cancelacion automatica. Orientacion bloqueada en vertical; la rotacion EXIF se aplica al decodificar.
Remuestreo propio: Preprocess reproduce cv2.INTER_LINEAR en Kotlin puro leyendo por filas, para que coincida con la referencia en Python y no depender de OpenCV ni de createScaledBitmap.
Plan B de la demo: seis muestras del conjunto publico (CC BY 4.0) en assets/samples, usables en Captura y como fuente de imagenes de una sesion.
Barrido: el orquestador usa interfaces (MotionController, FrameSource, FieldAnalyzer, ScanStore) para probarse en JVM con fallos inyectados; ManualMotionController, SimulatedMotionController y BleMotionController sobre SimulatedEsp32 cumplen la misma interfaz. Preparar el movimiento (conectar y HOME) ocurre en la fase PREPARING.
BLE: se implementa el protocolo y su simulador; el transporte GATT real no se escribe sin hardware ni UUID acordado. Tras perdida de conexion o timeout, el controlador detiene y exige HOME antes del siguiente movimiento.
Vista previa de la camara: en el emulador sin ventana salio negra en reposo aunque la captura funciono; se deja anotado como limitacion del emulador, sin confirmar en celular.