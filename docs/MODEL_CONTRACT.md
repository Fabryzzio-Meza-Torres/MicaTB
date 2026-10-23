# Contrato del modelo (Mica TB)

Este documento separa lo que ya se sabe de lo que hay que verificar. La app no debe tener valores del modelo escritos en el código: todo sale de models/model_config.json.

## 1. Lo que se sabe (viene del proyecto de entrenamiento)

- Arquitectura: YOLOv8n (Ultralytics). Tarea: detección de objetos.
- Entrenado con el Clinical Bacteria DataSet (frotis con tinción Gram). Es el modelo de la demo con E. coli. No es el modelo de TB.
- Archivos: ecoli_gb_E3s43_fp16.tflite (candidato, entrada 320 px, 6,45 MB), una versión INT8 y un respaldo de 416 px.
- Clases (4): G-cocci, G+cocci, G-bacilli, G+bacilli. El orden de índices debe confirmarse (ver sección 2).
- Operación elegida en validación: conf 0,60, NMS IoU 0,5, max_det 1000. Conteo estricto: solo G-bacilli. Conteo amplio: G-bacilli y G+bacilli.
- Referencias de calidad en test: precisión 0,87, recall 0,82, error de conteo 0,24 por imagen, mAP50 0,955. En 300 imágenes de paridad, FP32, FP16 y ONNX dieron mAP50 0,906 e INT8 0,874. Esas cifras son del dominio de entrenamiento, no del celular.
- Debilidades: campos densos, células pequeñas a 320 px, sensibilidad al brillo, brecha de dominio con fotos del celular.

## 2. Lo que hay que verificar (no asumir)

Inspecciona el .tflite con Python (paquete ai-edge-litert o tensorflow) y registra:

| Punto | Esperable, a confirmar | Cómo verificarlo |
|---|---|---|
| Tensor de entrada | 1 x 320 x 320 x 3, float32 (los pesos FP16 no cambian el tipo de entrada) | get_input_details() |
| Normalización | RGB, dividir entre 255, rango 0 a 1 | probar con una imagen y comparar con el .pt de Ultralytics |
| Preprocesado | letterbox con relleno gris 114, mantener proporción | comparar detecciones con y sin letterbox |
| Tensor de salida | 1 x 8 x 2100 (4 de caja y 4 de clases), sin objectness, sin NMS dentro | get_output_details(), forma y rango de valores |
| Cajas | xywh, centro y tamaño, normalizadas 0 a 1 respecto a la entrada (o en píxeles) | mirar el rango de los 4 primeros canales |
| Puntajes | ya con sigmoide aplicada, rango 0 a 1 | mirar el rango de los canales de clase |
| Orden de clases | G-cocci, G+cocci, G-bacilli, G+bacilli | leer el data.yaml del entrenamiento o metadatos del .tflite |
| Cuantización INT8 | parámetros de escala y punto cero de entrada y salida | get_input_details()[0]["quantization"] |

Si algo no coincide con lo esperable, se documenta en docs/DECISIONS.md y se corrige model_config.json. No se adivina.

## 3. Información que me debes proporcionar antes de la Fase 0

- Los archivos .tflite (FP16, INT8 y 416 px) y su hash sha256.
- El data.yaml (o la lista de clases con su índice) del entrenamiento.
- reference/reference_infer.py: script de Python que ejecuta el mismo .tflite con el mismo preprocesado y postprocesado que usará la app. Si aún no existe, la issue MT-01 lo crea.
- reference/test_vectors/: de 10 a 20 imágenes con su resultado esperado (expected.json) generado por ese script, incluyendo al menos 2 campos vacíos y 2 densos.
- docs/ref/: MODEL_CARD.md, ACCEPTANCE.md, RESUMEN.md y EDA_DECISIONES.md del entrenamiento.
- Modelo y versión de Android del celular de pruebas.

## 4. Esquema de models/model_config.json

Los valores de abajo son los esperables. Los marcados VERIFICAR deben confirmarse antes de usarlos.

```json
{
  "model_id": "ecoli_gb_E3s43_fp16",
  "file": "ecoli_gb_E3s43_fp16.tflite",
  "sha256": "VERIFICAR",
  "task": "detection",
  "domain_note": "Demo Gram E. coli. No es un detector de TB. No usar clínicamente.",
  "input": {
    "width": 320, "height": 320, "layout": "NHWC", "dtype": "float32",
    "color": "RGB", "scale": 0.00392157, "letterbox": true, "pad_value": 114
  },
  "output": {
    "shape": "VERIFICAR",
    "layout": "channels_first",
    "box_format": "xywh",
    "box_units": "VERIFICAR (normalized o pixels)",
    "has_objectness": false,
    "nms_in_model": false
  },
  "classes": ["G-cocci", "G+cocci", "G-bacilli", "G+bacilli"],
  "classes_verified": false,
  "postprocess": { "conf": 0.60, "nms_iou": 0.5, "max_det": 1000, "class_agnostic_nms": false },
  "counting": { "strict": ["G-bacilli"], "broad": ["G-bacilli", "G+bacilli"] }
}
```

Para el modelo de TB futuro solo cambian model_id, file, sha256, classes, counting y postprocess.

## 5. Pipeline de inferencia que debe implementar la app

1. Decodificar la foto aplicando la rotación EXIF. Trabajar en RGB.
2. Letterbox al tamaño de entrada manteniendo la proporción y rellenando con pad_value. Guardar escala y desplazamientos para deshacerlo.
3. Normalizar con scale y cargar el tensor en el layout indicado.
4. Ejecutar el intérprete (CPU, 4 hilos al inicio).
5. Decodificar la salida: por cada ancla, la clase de mayor puntaje y su puntaje. Descartar lo menor que conf.
6. Convertir las cajas a esquinas, deshacer el letterbox y recortar a los límites de la imagen original.
7. NMS por clase (o el modo indicado en la configuración) con nms_iou y límite max_det.
8. Devolver detecciones (caja, clase, puntaje), conteo estricto, conteo amplio y tiempos de preprocesado, inferencia y postprocesado.

Optimización pedida: el umbral conf del deslizador se aplica sobre las detecciones crudas guardadas en memoria, de modo que cambiarlo no obliga a recapturar ni a volver a ejecutar el modelo. Ejecutar el modelo con un umbral base bajo (por ejemplo 0,25) y filtrar después. Documentar esta decisión en docs/DECISIONS.md.

## 6. Prueba de paridad (compuerta de la Fase 1)

Objetivo: comprobar que la app calcula lo mismo que Python con el mismo archivo .tflite.

- Python (reference_infer.py) ejecuta el .tflite sobre cada imagen de reference/test_vectors/ y escribe expected.json con detecciones después de NMS (caja en píxeles originales, clase, puntaje) y los conteos.
- La app corre las mismas imágenes en una prueba instrumentada en el celular (connectedDebugAndroidTest).
- Criterio: mismo conteo estricto en todas las imágenes. Emparejando cajas de forma voraz por IoU, IoU mayor o igual a 0,95 y diferencia de puntaje menor o igual a 0,01.
- Si falla: no se avanza. Revisar en este orden: RGB contra BGR, escala 0 a 1, unidades de las cajas, deshacer el letterbox, rotación EXIF, orden de clases.
- Prueba aparte, hecha en Kaggle y no en la app: el .pt contra el .tflite exportado en las mismas 300 imágenes, para descartar pérdidas por la exportación.

## 7. Protocolo de benchmark en el celular

- 5 inferencias de calentamiento y 50 medidas sobre la misma imagen.
- Reportar mediana y p95 por separado de preprocesado, inferencia y postprocesado.
- Variantes: FP16 CPU 4 hilos, INT8 CPU 4 hilos, y GPU si se habilita. Comprobar que el conteo no cambia entre delegados.
- Registrar modelo del celular, versión de Android y temperatura aproximada (tras 5 minutos de uso continuo, repetir una vez para ver estrangulamiento térmico).
- Objetivo inicial: mediana de inferencia menor que 150 ms (criterio A6 del entrenamiento). Es un valor a validar, no una promesa.

## 8. Umbral y dominio

El conf 0,60 se eligió con imágenes del dataset. Con fotos del celular los puntajes pueden bajar. Por eso conf es ajustable en la app y se recalibra con unas decenas de campos propios (fase de capturas propias). Si en el pase cualitativo con 20 a 30 campos reales el modelo casi no detecta nada, el primer paso es mirar los puntajes y el brillo, antes de tocar el modelo.
