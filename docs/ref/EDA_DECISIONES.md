# EDA_DECISIONES.md (parcial, kernel ecoli-datos-v2)

| Hallazgo (salida real) | Decision |
|---|---|
| 6005 img / 11824 cajas; clases 3371/1462/5799/1192 (coincide con Zenodo) | Orden de clases de DetectionDataSet se mantiene |
| 120 grupos md5 duplicados -> 5872 img tras dedupe | Una copia por md5 (antes de agrupar) |
| Union-find id + dHash<=3: 2111 grupos, mayor 46 (0,8 %), 1811 de tamano 1 | No hace falta romper componentes (<2 %) |
| Pares dHash<=3: solo 3 de 30 sorteados tenian ids distintos; todos misma escena (ids consecutivos, p. ej. 000231/000232) | Confirma duplicados reales. Riesgo residual: ids consecutivos podrian ser cuadros vecinos sin ser casi duplicados |
| Split semilla 3: a,b,c,d,e cumplen (d sin relajar: 13,2-16,8 %; G+bacilli val 189, test 160) | Congelado, sha256 manifest b7bd3cfd...c701 |
| 640DataSet vs DetectionDataSet: cajas identicas (IoU 1,0). 640 es binario: Detection 0,1 -> 0; 2,3 -> 1 (cocos vs bacilos) | No hay conflicto; se usa DetectionDataSet |
| Ventanas 320x320 sin cajas: revision visual de 100 -> claramente mas de 5 % con bacterias/celulas sin etiquetar | Fondos sinteticos DESCARTADOS (E5 no se corre); precision subestimada |
| Baselines val: mediana=1 -> MAE 0,734 (estricta), 0,771 (amplia); umbral Lab MAE 3,08 | La red debe ganar a 0,73 |
| E1/E2/E3 (640/416/320) en val: mAP50 Gb 0,943/0,946/0,948; MAE 0,287/0,299/0,297 con ICs solapados | Dentro del ruido: se elige 320 (mas rapido: 34 ms vs 89 ms CPU pt). Riesgo: campos densos de la camara real |
| mAP50 Gb ~0,94 en val agrupado, mayor al ~0,73 esperado; recall ~0,80 < 0,85 | Sospecha de dominio facil o leakage residual; se investiga en paso 5 (crop vs simple, canario) |
| Paso 5, G-bacilli por densidad (val): cuartil con 1–16 cajas/imagen recall 0,72, MAE 0,74; el resto MAE 0,09–0,26 | Los campos densos degradan: capturar campos densos en fase 2 y reentrenar; no confiar en el MAE global para campos de cultivo |
| Paso 5, tamano de caja: lado 25–43 px (@640) recall 0,69 vs 0,89 en las mas grandes | A 320 px esas celulas miden 12–21 px; si falla en el celular, probar 416 o recortes |
| Paso 5, brillo: cuartil mas claro (gris > 215) precision 0,79, MAE 0,46; cuartil mas oscuro recall 0,74 | Variar iluminacion en la captura propia; considerar normalizacion de brillo |
| Paso 5, 24 peores imagenes: aprox. la mitad de los FP visibles caen sobre objetos con forma de bacilo sin caja (estimacion a ojo, sesgada a los peores casos) | Precision subestimada; no tomar A2 como cota inferior fiable; reetiquetar un subconjunto o medir en laminas propias |
| Paso 5, leakage: crop vs simple casi iguales (F1 0,823 vs 0,811 en val); correlacion rendimiento–similitud con train ~0 (Spearman 0,05 dHash; −0,11 histograma Lab) | Sin evidencia de leakage residual. mAP50 ~0,95 probablemente por cajas centradas en manchas oscuras faciles con IoU 0,5 permisivo; no se puede descartar del todo |
| Paso 6, rejilla conf × IoU de NMS (val): optimo por MAE conf 0,60 / NMS 0,5; optimo F1 conf 0,50 | Se congela conf 0,60, NMS 0,5, max_det 1000. MAE plano entre conf 0,5 y 0,65 |
| E4 (sin variacion de color) dentro del ruido entre semillas de E3 (recall 0,79–0,82) | Se mantiene color por defecto |
| INT8: mAP50 G-bacilli −3,2 pts vs FP32 (A4 no cumplido) | Se recomienda FP16 |
| Paridad: .pt 0,9525 (rect=True) vs 0,9058 (rect=False) en las mismas 300 imagenes; test completo 0,955 vs 0,910; conversion identica | Reportar el mAP50 comparable con la app (0,91); no atribuirlo a la conversion ni al muestreo |
| Ids vecinos: 84 % de test con vecino en train; sin diferencia a favor de los que tienen vecino | Sin evidencia de fuga; documentar poca potencia |
| Script de referencia coincide con ultralytics 300/300 | La app debe replicar `inferir_tflite.py` |
| Paridad: `val(rect=True)` 0,955 vs `rect=False` 0,910 en test completo; el `.pt` y los TFLite dan lo mismo en la ruta de inferencia; LINEAR = AREA en 640→320 | No es conversión ni redimensionado: `rect=True` usa lienzo de 352 px |
| PAD352 (320 + 16 px de borde gris) en val: F1 0,832 → 0,876, MAE 0,288 → 0,218 | Se adopta como preprocesado de la app (regla pre-registrada, Adenda 2) |
| Ids vecinos (ruta 320): con vecino a distancia 1 recall 0,804, sin vecino 0,880 | Sin señal de fuga; a distancia 2 casi todas tienen vecino (poco poder) |
| Script de referencia vs ultralytics: 300/300 conteos iguales; 60 recortes no cuadrados con IoU > 0,7 | La app debe replicar `inferir_tflite.py` |
| Ruta final en test: recall 0,874 (IC 0,828–0,919), media de semillas 0,858 | A1 cumplido en estimación puntual; no se puede afirmar con certeza |
