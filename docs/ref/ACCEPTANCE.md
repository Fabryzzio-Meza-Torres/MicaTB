# ACCEPTANCE.md (pre-registro y veredicto)

Fecha de fijacion: 2026-10-17, antes de ver cualquier metrica de val/test. Los umbrales no se movieron.
Propuestos por el usuario, sin base empirica. Evaluacion en test agrupado (split por grupos de origen, sin leakage), un solo uso por modelo congelado.

| # | Criterio | Umbral |
|---|----------|--------|
| A1 | G-bacilli: recall al conf congelado | >= 0,85 |
| A2 | G-bacilli: precision al conf congelado | >= 0,80 |
| A3 | MAE de conteo por imagen | <= 0,5 |
| A4 | INT8 vs FP32: perdida de mAP50 en G-bacilli | < 2 puntos |
| A5 | INT8 vs FP32: MAE de conteo | no peor que +10 % relativo |
| A6 | Latencia proxy (CPU Kaggle, solo relativa) | < 150 ms |

## Adenda 1 (2026-10-19, antes de evaluar test)
Config elegida en val: yolov8n, 320 px, color por defecto. Semilla elegida por mayor F1 de val al conf de minimo MAE (resulto 43). Umbrales: minimo MAE de conteo estricto en val (conf 0,05–0,70 x IoU de NMS {0,5; 0,6; 0,7}); max_det 1000. Paridad en 300 imagenes de test al azar (semilla 42). INT8 con calibracion de 300 imagenes de train estratificadas por brillo y Lab b.

## Adenda 2 (2026-10-20, antes de evaluar la variante)
Hallazgo: el mAP50 de `val` de ultralytics era 0,955 con `rect=True` y 0,910 con `rect=False`; el AP50 propio sobre la ruta de inferencia (la de la app) era 0,892. No era conversion ni muestreo: el `.pt` y los TFLite dan lo mismo en la misma ruta. Se descarto el redimensionado (en 640->320 LINEAR y AREA son identicos). Hipotesis: `rect=True` trabaja con un lienzo de 352 px (320 + relleno).
Variante **PAD352** (redimensionar a 320 y rellenar 16 px de gris 114 por lado). Regla: adoptar solo si en VAL mejora el F1 de G-bacilli sin empeorar el MAE. Si se adopta: reexportar a 352, congelar conf/NMS en val y evaluar test una vez, reportandola junto a la ruta de 320.
Resultado en val (conf por minimo MAE, NMS 0,5): F1 0,832 -> 0,876, recall 0,818 -> 0,866, precision 0,846 -> 0,887, MAE 0,288 -> 0,218. **Se adopta.**
Transparencia: la hipotesis nacio de comparar numeros de test (paridad en 300 imagenes), aunque la decision se tomo solo con val.

## Veredicto (2026-10-20)

### Ruta final adoptada: PAD352, yolov8n entrenado a 320, semilla 43, conf 0,45, NMS 0,5, max_det 1000
Test agrupado (890 imagenes), IC 95 % por bootstrap de grupos de origen.

| # | Criterio | Umbral | Resultado | Veredicto |
|---|---|---|---|---|
| A1 | Recall G-bacilli | >= 0,85 | **0,874** (0,828–0,919). Media de 3 semillas **0,858 ± 0,015** (42: 0,855; 43: 0,874; 44: 0,845) | **CUMPLIDO en la estimacion puntual**; el IC baja de 0,85 y una semilla queda en 0,845 |
| A2 | Precision G-bacilli | >= 0,80 | 0,901 (0,871–0,924); 3 semillas 0,899 ± 0,011 | CUMPLIDO (sesgada a la baja por bacterias sin etiquetar) |
| A3 | MAE de conteo | <= 0,5 | 0,185 (0,138–0,245); 3 semillas 0,204 ± 0,024 | CUMPLIDO |
| A4 | INT8: perdida de mAP50 vs FP32 | < 2 pts | AP50 en 300 imagenes de test: FP32 0,898, INT8 0,902 (sin perdida) | **CUMPLIDO** (muestra pequena: 258 bacilos) |
| A5 | INT8: MAE vs FP32 | <= +10 % | 0,183 vs 0,170 (+7,8 %) | **CUMPLIDO, con poco margen** |
| A6 | Latencia proxy | < 150 ms | CPU de Kaggle: el script de referencia (con NMS en numpy) tarda ~60 ms/imagen en la ruta de 352; el ultralytics a 320 media 22–26 ms | CUMPLIDO como proxy; la real en el celular esta pendiente |

### Ruta anterior (320 sin borde), solo como referencia
A1 **NO CUMPLIDO** (recall 0,819; IC 0,775–0,862; 3 semillas 0,787 ± 0,029). A4 **NO CUMPLIDO** (INT8 −3,2 pts de mAP50 frente a FP32: 0,906 → 0,874 en 300 imagenes de test, medido con `val`). A2, A3, A5 y A6 cumplidos.

### Recomendacion
**INT8** (3,4 MB) cumple A4 y A5 en la ruta final; **FP16** (6,5 MB) como alternativa si la latencia o la exactitud en el celular no convencen. El INT8 se calibro con imagenes a 352 de lado completo y no con el lienzo 320 + borde, una diferencia pequena que conviene revalidar en el celular.

### Lo que este veredicto no dice
- Las metricas son sobre el dataset publico (otro dominio). Para las laminas propias de E. coli son un punto de partida, no una validacion.
- La precision y el MAE estan afectados por bacterias sin etiquetar; no son cotas fiables.
- A1 se cumple por poco: no se puede afirmar que el recall verdadero sea >= 0,85 (el IC incluye 0,83).
