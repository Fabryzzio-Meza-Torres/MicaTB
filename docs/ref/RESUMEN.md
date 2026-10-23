# RESUMEN — detector G-bacilli (E. coli) para MicaTB

**Modelo recomendado:** YOLOv8n entrenado a 320 px, semilla 43, **con 16 px de borde gris en la entrada (352×352)**, TFLite **INT8** (`modelos/ecoli_gb_E3s43_352_int8.tflite`, 3,4 MB); FP16 (6,5 MB) como alternativa. Conteo estricto (solo G-bacilli), conf 0,45, NMS 0,5, max_det 1000. Es un punto de partida entrenado con un dataset público de otro dominio, no un modelo validado en láminas propias ni un diagnóstico.

## Resultado en test (890 imágenes, una evaluación por modelo congelado, IC 95 % por grupos de origen)
| | Modelo elegido | 3 semillas |
|---|---|---|
| Precisión | 0,901 (0,871–0,924) | 0,899 ± 0,011 |
| Recall | 0,874 (0,828–0,919) | 0,858 ± 0,015 |
| MAE de conteo | 0,185 (0,138–0,245) | 0,204 ± 0,024 |
Baselines: mediana MAE 0,737; umbral de color MAE 3,18.

## Criterios de ACCEPTANCE.md
- **Ruta final (con borde):** A1 cumplido en la estimación puntual (0,874; media 0,858; el IC baja a 0,828; una semilla da 0,845), A2, A3, A4, A5 (con poco margen) y A6 (proxy) cumplidos.
- **Ruta anterior (320 sin borde):** A1 (recall 0,819) y A4 (INT8 pierde 3,2 pts de mAP50) **no cumplidos**.

## Experimentos (todos en val, 100 épocas, patience 25, batch 16; ruta anterior sin borde)
| ID | Cambio | Semilla | imgsz | mAP50 Gb | MAE | P | R |
|---|---|---|---|---|---|---|---|
| E1 | base COCO | 42 | 640 | 0,943 | 0,287 | 0,843 | 0,809 |
| E2 | resolución | 42 | 416 | 0,946 | 0,299 | 0,829 | 0,822 |
| E3 | resolución | 42 | 320 | 0,948 | 0,297 | 0,849 | 0,793 |
| E4 | sin variación de color | 42 | 320 | 0,943 | 0,277 | 0,857 | 0,804 |
| E3s43 | semilla | 43 | 320 | 0,950 | 0,289 | 0,845 | 0,818 |
| E3s44 | semilla | 44 | 320 | 0,946 | 0,308 | 0,858 | 0,777 |
| PAD352 | borde de 16 px (inferencia) | 43 | 320 → 352 | AP50 propio 0,924 | 0,218 | 0,887 | 0,866 |
Detalle en `experiments.csv`. E5 (fondos) no se corrió; no se hicieron los condicionales.

## Decisiones y motivos
1. **Split por grupos**, no el de los autores: 80 % de los ids con recortes estaban repartidos entre splits.
2. **Sin fondos sintéticos**: más del 5 % de las ventanas "vacías" tenía bacterias sin etiquetar.
3. **320 px**: 640/416/320 indistinguibles dentro del ruido; se elige la más rápida.
4. **Color por defecto**: E4 no mejora más que la variación entre semillas.
5. **Semilla 43** por mayor F1 en val (regla pre-registrada).
6. **Borde de 16 px (PAD352)**: se descubrió al explicar por qué el mAP50 de `val` (0,955) y el de las exportaciones (0,906) no coincidían; no era conversión, era `rect=True` (lienzo de 352). Se decidió en val (F1 0,832 → 0,876) con regla pre-registrada.
7. **INT8 aceptado** en la ruta final (A4/A5 cumplidos); en la ruta anterior se habría recomendado FP16.
8. **Conteo estricto** recomendado (MAE test 0,185 vs 0,200 el amplio); el amplio queda como plan B.

## Verificaciones
Paridad pt/FP32/FP16/INT8 en 300 imágenes de test; script de referencia sin ultralytics idéntico a ultralytics (300/300 conteos); geometría validada en 60 recortes no cuadrados; sin señal de fuga por ids vecinos; 20 casos de prueba con etiqueta y predicción en `casos_prueba/`.

## Entregables
`modelos/`, `config.json`, `app_config.json`, `inferir_tflite.py`, `deploy_notes.md`, `casos_prueba/`, `ACCEPTANCE.md`, `MODEL_CARD.md`, `GUION_DEMO.md`, `EDA_DECISIONES.md`, `experiments.csv`, `split_manifest.csv`, `requirements.txt`, `notebooks/`, `figuras/`, `tablas/`, `SHA256SUMS.txt`.
