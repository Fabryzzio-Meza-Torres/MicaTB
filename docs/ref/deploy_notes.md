# deploy_notes.md — para el desarrollador Android

Modelo recomendado: **`modelos/ecoli_gb_E3s43_352_int8.tflite`** (3,4 MB; cumple A4/A5, ver `ACCEPTANCE.md`). Alternativa: `..._fp16.tflite` (6,5 MB). Referencia: `..._fp32.tflite`, `.onnx`, `.pt`.
**Todo lo de abajo está en `app_config.json` y se reproduce con `inferir_tflite.py`** (referencia ejecutable; la app debe dar lo mismo). `casos_prueba/` trae 20 imágenes con su resultado esperado.

## 1. Preprocesado (obligatorio, incluido el borde)
1. Imagen del cuadro, cualquier tamaño.
2. **Letterbox a 320**: `r = min(320/alto, 320/ancho)`; redimensionar a `round(ancho·r) × round(alto·r)` con interpolación **bilineal**; rellenar con gris **114** hasta 320×320, centrando (`top = round(dh−0,1)`, `left = round(dw−0,1)` con `dh = (320 − nuevo_alto)/2`).
3. **Agregar 16 px de gris 114 por cada lado** → entrada **352×352**. (Sin este borde baja el recall; es parte del modelo.)
4. Convertir a **RGB**, `float32`, dividir por **255** (0–1), sin media ni desviación, orden **NCHW**: tensor `[1, 3, 352, 352]`.

Para una imagen cuadrada de 640×640 el paso 2 es un redimensionado directo a 320.

## 2. Salida del modelo
- Tensor `[1, 8, 2541]` float32 (2541 = 44² + 22² + 11² anclas). **También en INT8**: entrada y salida siguen siendo float32 (cuantización 0 / zero point 0); el INT8 es interno.
- Filas 0–3: `cx, cy, w, h` **normalizados 0–1 respecto a 352**. Filas 4–7: score por clase (0–1, sin sigmoide extra), orden `0 G-cocci, 1 G+cocci, 2 G-bacilli, 3 G+bacilli`.
- **El NMS NO viene incluido**: va en la app.

## 3. Decodificación y conteo (valores congelados en val)
1. `cajas = (cx−w/2, cy−h/2, cx+w/2, cy+h/2) × 352`; `score = max(filas 4..7)`, `clase = argmax`.
2. Quedarse con `score > conf`: **0,45** (definición estricta) o **0,40** (amplia).
3. **NMS por clase**, IoU **0,5** (suprimir si IoU > 0,5), `max_det = 1000`.
4. Deshacer el preprocesado: `x_orig = (x − left_total) / r`, `y_orig = (y − top_total) / r`, con `left_total = left + 16`, `top_total = top + 16`; recortar al tamaño de la imagen.
5. **Conteo recomendado (estricto):** número de cajas cuya clase ganadora es **2 (G-bacilli)**. Conteo amplio (si el E. coli sale violáceo): clases 2 y 3 con conf 0,40.

## 4. Verificación hecha (en Kaggle)
- `inferir_tflite.py` vs ultralytics: mismo conteo en 300/300 imágenes, cajas dentro de 0,05 px.
- Geometría en imágenes no cuadradas: 60 recortes de 5 formas; 53 detectados, todos con IoU > 0,7 frente a la caja de la imagen completa (mediana 0,93; error de centro 0,7 px).
- Referencia para la app: `casos_prueba/casos_prueba.json` (conteos y cajas esperados). Criterio de aceptación sugerido: mismo conteo en todos los casos y cajas dentro de ~1 px.

## 5. Latencia
Proxy en CPU de Kaggle: el script de referencia (con NMS en numpy y umbral muy bajo) tarda ~60 ms por imagen; con ultralytics a 320 se midieron 22–26 ms. La latencia real en el celular de gama baja la mides tú (objetivo < 150 ms).

## 6. Dependencias del script de referencia
`numpy`, `opencv-python`, `ai-edge-litert` (ver `requirements_app_referencia.txt`).
