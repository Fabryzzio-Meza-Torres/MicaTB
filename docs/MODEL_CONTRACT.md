# Contrato del modelo (Mica TB)

La app no debe tener valores del modelo escritos en el código: todo sale de models/model_config.json. Este documento recoge lo que se sabe, lo que se verificó en MT-01 y lo que queda por verificar.

## 1. Lo que se sabe (viene del proyecto de entrenamiento)

- Arquitectura: YOLOv8n (Ultralytics). Tarea: detección de objetos.
- Entrenado con el Clinical Bacteria DataSet (frotis con tinción Gram, CC BY 4.0). Es el modelo de la demo con E. coli. No es el modelo de TB.
- Ruta PAD352: se entrena a 320. En inferencia se hace letterbox a 320 y se añaden 16 px de gris por lado, así que la entrada es 352 x 352. El borde es parte del modelo: sin él el recall de validación baja de 0,866 a 0,818.
- Archivos: models/ecoli_gb_E3s43_352_fp16.tflite (por defecto, 6,46 MB) y models/ecoli_gb_E3s43_352_int8.tflite (3,4 MB, variante a medir). La ruta anterior de 320 sin borde y el respaldo de 416 (exportado sin borde, no probado con él) quedan fuera de la app.
- Clases (4): G-cocci, G+cocci, G-bacilli, G+bacilli.
- Operación elegida en validación: NMS IoU 0,5, max_det 1000. Conteo estricto: solo G-bacilli con conf 0,45. Conteo amplio: G-bacilli y G+bacilli con conf 0,40.
- Referencias de calidad en test (890 imágenes, FP32 de ultralytics): precisión 0,901, recall 0,874 (IC 95 % por grupos 0,828 a 0,919; media de 3 semillas 0,858), error de conteo 0,185 por imagen (IC 0,138 a 0,245). Son cifras del dominio de entrenamiento, no del celular. Detalle y límites en docs/ref/ACCEPTANCE.md y docs/ref/MODEL_CARD.md.
- Debilidades: campos densos, células pequeñas, sensibilidad al brillo, brecha de dominio con fotos del celular. El recall cumple por poco.

## 2. Verificado en MT-01 (inspección de los .tflite con ai-edge-litert 2.2.0)

| Punto | Resultado | Cómo se verificó |
|---|---|---|
| Tensor de entrada | [1, 3, 352, 352], float32, NCHW, cuantización (0.0, 0) en FP16 e INT8 | get_input_details() |
| Normalización | RGB, píxel / 255, rango 0 a 1, sin media ni desviación | reference_infer.py reproduce los conteos de Kaggle |
| Preprocesado | letterbox a 320 (bilineal, relleno 114, centrado) más 16 px de 114 por lado | idem |
| Tensor de salida | [1, 8, 2541], float32, sin objectness, sin NMS dentro. 2541 = 44² + 22² + 11² | get_output_details() |
| Cajas | filas 0 a 3 = cx, cy, w, h normalizados respecto a 352 (rango observado 0,005 a 1,07) | rango de los canales sobre una imagen |
| Puntajes | filas 4 a 7, ya activados, rango 0 a 1 (observado 0 a 0,83) | idem |
| Orden de clases | 0 G-cocci, 1 G+cocci, 2 G-bacilli, 3 G+bacilli | app_config.json del entrenamiento y detecciones en imágenes con G-bacilli conocidos |
| INT8 | entrada y salida siguen en float32; el INT8 es interno. Mismos tensores que FP16 | get_input_details() y get_output_details() |
| Hashes | coinciden con docs/ref/SHA256SUMS.txt del entrenamiento | sha256 |

## 3. Material de la Fase 0 (estado)

- Modelos y sha256: en models/ y model_config.json.
- Clases con su índice: en model_config.json.
- reference/reference_infer.py: lee model_config.json y genera expected.json.
- reference/test_vectors/: 23 imágenes. 20 con resultado independiente de Kaggle (kaggle_expected.json) y 3 no cuadradas sin resultado de Kaggle, solo el del script. Entre los 20 comparables hay 5 con conteo estricto 0 y campos con 5 y 16 bacilos.
- docs/ref/: resultados del entrenamiento, solo lectura.
- Pendiente: modelo y versión de Android del celular de pruebas.

## 4. Esquema de models/model_config.json

Es el archivo real de models/. Para el modelo de TB futuro cambian model_id, file, sha256, input, output, classes, counting y postprocess.

```json
{
  "model_id": "ecoli_gb_E3s43_352_fp16",
  "file": "ecoli_gb_E3s43_352_fp16.tflite",
  "sha256": "08538648...f980b3",
  "variants": { "int8": { "file": "ecoli_gb_E3s43_352_int8.tflite", "sha256": "da776aab...047234" } },
  "task": "detection",
  "domain_note": "Demo Gram E. coli. No es un detector de TB. No usar clinicamente.",
  "input": {
    "width": 352, "height": 352, "content": 320, "border": 16,
    "layout": "NCHW", "dtype": "float32", "color": "RGB",
    "scale": 0.00392156862745098, "letterbox": true, "pad_value": 114, "interpolation": "bilinear"
  },
  "output": {
    "shape": [1, 8, 2541], "layout": "channels_first", "box_format": "xywh",
    "box_units": "normalized", "has_objectness": false, "scores_activated": true, "nms_in_model": false
  },
  "classes": ["G-cocci", "G+cocci", "G-bacilli", "G+bacilli"],
  "classes_verified": true,
  "postprocess": { "base_conf": 0.25, "nms_iou": 0.5, "max_det": 1000, "class_agnostic_nms": false },
  "counting": {
    "recommended": "strict",
    "strict": { "classes": ["G-bacilli"], "conf": 0.45 },
    "broad": { "classes": ["G-bacilli", "G+bacilli"], "conf": 0.40 }
  }
}
```

## 5. Pipeline de inferencia que debe implementar la app

1. Decodificar la foto aplicando la rotación EXIF. Trabajar en RGB.
2. Letterbox a input.content (320) manteniendo la proporción: r = min(320/alto, 320/ancho), redimensionar a round(ancho·r) x round(alto·r) con interpolación bilineal, rellenar con pad_value centrando (top = round(dh - 0,1), left = round(dw - 0,1), con dh y dw la mitad del relleno que falta). Guardar r, left y top.
3. Añadir input.border (16) px de pad_value por cada lado. El desplazamiento total es left + 16 y top + 16.
4. Multiplicar por scale y cargar el tensor NCHW [1, 3, 352, 352].
5. Ejecutar el intérprete (CPU, 4 hilos al inicio).
6. Decodificar: por cada ancla, la clase de mayor puntaje y su puntaje. Descartar lo que no supere base_conf (0,25). Cajas: (cx - w/2, cy - h/2, cx + w/2, cy + h/2) por 352.
7. NMS por clase con nms_iou. Ordenar por puntaje y recortar a max_det.
8. Deshacer el preprocesado: x_orig = (x - left_total) / r, y_orig = (y - top_total) / r, y recortar a los límites de la imagen original.
9. Devolver las detecciones crudas (caja, clase, puntaje), los conteos estricto y amplio y los tiempos de preprocesado, inferencia y postprocesado.

Los conteos aplican "puntaje > conf" (estrictamente mayor) de cada definición, sobre las clases de esa definición. Filtrar después del NMS da el mismo resultado que filtrar antes, porque el NMS voraz por clase solo deja que las cajas de mayor puntaje supriman a las de menor puntaje. Por eso el deslizador de conf se aplica sobre las detecciones crudas guardadas en memoria, sin recapturar ni volver a ejecutar el modelo. Decisión registrada en docs/DECISIONS.md.

## 6. Prueba de paridad (compuerta de la Fase 1)

Objetivo: comprobar que la app calcula lo mismo que Python con el mismo archivo .tflite.

- Python (reference/reference_infer.py) ejecuta el .tflite sobre cada imagen de reference/test_vectors/ y escribe expected.json con las detecciones después de NMS a base_conf (caja en píxeles originales, clase, puntaje) y los dos conteos.
- La app corre las mismas imágenes en una prueba instrumentada en el celular (connectedDebugAndroidTest).
- Criterio: mismo conteo estricto en todas las imágenes. Emparejando cajas de forma voraz por IoU, IoU mayor o igual a 0,95 y diferencia de puntaje menor o igual a 0,01.
- Si falla: no se avanza. Revisar en este orden: RGB contra BGR, escala 0 a 1, unidades de las cajas, deshacer letterbox y borde, rotación EXIF, orden de clases.
- Prueba aparte, hecha en Kaggle y no en la app: ultralytics contra el .tflite exportado en las mismas 300 imágenes. Resultado: mismo conteo en 300 de 300 y cajas dentro de 0,05 px.

Resultado de MT-04 (emulador x86_64, Android 35, LiteRT 2.3.0): 23 de 23 imágenes con conteo estricto y amplio idénticos, IoU mínimo 0,997 y diferencia máxima de puntaje 0,00005. El criterio se cumple sin cambios. Pendiente repetirlo en un celular real con ARM.

## 7. Protocolo de benchmark en el celular

- 5 inferencias de calentamiento y 50 medidas sobre la misma imagen.
- Reportar mediana y p95 por separado de preprocesado, inferencia y postprocesado.
- Variantes: FP16 CPU 4 hilos, INT8 CPU 4 hilos, y GPU si se habilita. Comprobar que el conteo no cambia entre delegados.
- Registrar modelo del celular, versión de Android y temperatura aproximada (tras 5 minutos de uso continuo, repetir una vez para ver estrangulamiento térmico).
- Objetivo inicial: mediana de inferencia menor que 150 ms (criterio A6 del entrenamiento). Es un valor a validar, no una promesa.

## 8. Umbral y dominio

Los umbrales 0,45 y 0,40 se eligieron con imágenes del dataset. Con fotos del celular los puntajes pueden bajar. Por eso conf es ajustable en la app y se recalibra con unas decenas de campos propios (fase de capturas propias). Si en el pase cualitativo con 20 a 30 campos reales el modelo casi no detecta nada, el primer paso es mirar los puntajes y el brillo, antes de tocar el modelo.
