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

## 2026-10-23 Tolerancia de puntaje en la paridad (ABIERTA)

Contexto: MODEL_CONTRACT.md sección 6 exige diferencia de puntaje menor o igual a 0,01. Entre reference_infer.py (Windows) y los resultados de Kaggle con el mismo FP16 hay diferencias de hasta 0,024 en una detección con puntaje cercano a 0,5, con conteos y cajas iguales.
Decisión: no se cambia el criterio todavía. Se decide en MT-04 con la medición en el celular. Si hace falta, ensanchar el puntaje a unos 0,03 y mantener conteo idéntico e IoU mayor o igual a 0,95. Pendiente de confirmación del equipo.
## 2026-10-23 Toolchain Android (MT-02)

Contexto: versiones verificadas en la documentación oficial el mismo día (developer.android.com y kotlinlang.org), no de memoria.
Decisión: AGP 9.4.0 con Gradle 9.6.0 (mínimo exigido por esa versión; distribución verificada con su sha256 en gradle-wrapper.properties). AGP 9 trae Kotlin integrado, así que no se aplica kotlin-android; solo el plugin de Compose (versión 2.4.21, la estable actual de Kotlin). Compose BOM 2026.09.00, Navigation Compose 2.10.2, Activity Compose 1.13.0. compileSdk 37 porque las dependencias actuales de Compose y lifecycle lo exigen (con 36 el build falla); targetSdk 36 y minSdk 26 se mantienen, son decisiones separadas.
JDK: el que trae Android Studio (JBR 25) vía JAVA_HOME; el build funcionó con él. Android Studio, command-line tools (sha256 verificado) y SDK (platform-tools, plataformas 36 y 37.0, build-tools 36.0.0) se instalaron con winget y sdkmanager, con las licencias aceptadas.
Assets: app/build.gradle.kts añade ../models como directorio de assets, así models/ sigue siendo la única fuente del modelo y de model_config.json. noCompress para tflite. El APK de depuración incluye FP16, INT8 y la configuración (21,8 MB).
Parseo de la configuración: org.json de Android en la app y la librería org.json:json solo en pruebas JVM (las pruebas leen el model_config.json real de models/).
Descartado: aplicar kotlin-android (según la guía oficial, el Kotlin integrado de AGP 9 lo reemplaza) y un JDK 17 aparte (el build funcionó con el JBR).