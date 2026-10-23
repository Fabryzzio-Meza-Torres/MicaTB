# MODEL_CARD — detector de bacilos Gram negativos (YOLOv8n, ruta 320 + borde = entrada 352)

## Uso previsto
Contar bacilos Gram negativos (rosados) por campo en la demostración en vivo de MicaTB (celular + microscopio óptico, E. coli en láminas Gram fijadas, bioseguridad nivel 1). **Es un punto de partida, no un modelo validado para las láminas propias. No es un resultado clínico ni un diagnóstico, y no es apto para diagnóstico.**

## Modelo y ruta de inferencia
YOLOv8n preentrenado en COCO, ajustado 100 épocas a 320 px (semilla 43, elegida por val entre 42/43/44), 3,0 M de parámetros. Cuatro clases (G-cocci, G+cocci, G-bacilli, G+bacilli); se cuenta solo G-bacilli. **Ruta de inferencia obligatoria:** redimensionar a 320 y añadir 16 px de borde gris (114) por lado → entrada 352×352. Sin ese borde el rendimiento baja (recall en val 0,866 → 0,818). Conteo con conf 0,45, NMS 0,5, max_det 1000. Archivos y sha256 en `modelos/` y `config.json`.

## Datos
Clinical Bacteria DataSet (Wang X., 2024), Zenodo 10.5281/zenodo.10526360 v3, CC BY 4.0, Sci Data 11, 608 (2024). 6005 imágenes 640×640 y 11824 cajas; 5872 imágenes tras quitar duplicados exactos. Split propio por grupos (id de origen + dHash ≤ 3, union-find; 2111 grupos): 4100 / 882 / 890 imágenes. Ningún grupo, md5 ni par casi duplicado cruza splits (`split_manifest.csv`, sha256 b7bd3cfd…c701).

## Métricas en test (G-bacilli), IC 95 % por bootstrap de grupos de origen
| Métrica | Modelo elegido (semilla 43) | 3 semillas (media ± sd) |
|---|---|---|
| Precisión | 0,901 (0,871–0,924) | 0,899 ± 0,011 |
| Recall | 0,874 (0,828–0,919) | 0,858 ± 0,015 |
| F1 | 0,888 (0,859–0,913) | 0,878 ± 0,011 |
| MAE de conteo por imagen | 0,185 (0,138–0,245) | 0,204 ± 0,024 |
| AP50 propio (ruta de la app) | 0,927 | 0,924 ± 0,006 |
| mAP50 ultralytics `val` (rect=True) | 0,955 | — |

Por patrón (elegido): recortes F1 0,889 / MAE 0,194; simples F1 0,885 / MAE 0,166. Conteo amplio (G− + G+ bacilos, conf 0,40): MAE 0,200. Baselines en test: mediana de train MAE 0,737; umbral de color Lab MAE 3,18. Matriz de confusión en `tablas/confusion_test_pad352.csv`: de 844 bacilos G−, 738 bien, 20 como G-cocci, 9 como G+bacilli, 77 sin detectar; 39 falsos positivos desde fondo y 27 G+bacilli predichos como G−. Fondos sintéticos de test (proxy sesgado): 0,04 falsos positivos por imagen.

## Exportaciones y paridad (300 imágenes de test, ruta final)
| Formato | AP50 propio G-bacilli | Precisión | Recall | MAE | Tamaño |
|---|---|---|---|---|---|
| PyTorch con borde | 0,898 | 0,899 | 0,860 | 0,170 | 6,5 MB |
| TFLite FP32 | 0,898 | 0,899 | 0,860 | 0,170 | 12,7 MB |
| TFLite FP16 | 0,900 | 0,899 | 0,860 | 0,170 | 6,5 MB |
| TFLite INT8 | 0,902 | 0,898 | 0,853 | 0,183 | 3,4 MB |

El script `inferir_tflite.py` (sin ultralytics) da el mismo conteo que ultralytics en 300 de 300 imágenes (diferencia máxima de caja 0,05 px) y reproduce las cajas en 60 recortes no cuadrados (53 detectados, todos con IoU > 0,7 frente a la imagen completa; IoU mediana 0,93).

### Por qué existían dos cifras de mAP (0,955 y 0,906)
No era conversión ni muestreo. El mismo `.pt` en las mismas 300 imágenes da 0,953 con `val(rect=True)` y 0,906 con `rect=False`; con `rect=True` ultralytics trabaja con lienzo de 352 px (320 + relleno). Sobre la ruta de inferencia sin borde, el `.pt` y los TFLite coinciden exactamente. Se probó agregar ese borde al preprocesado: en val mejoró el F1 de 0,832 a 0,876 y se adoptó (ver `ACCEPTANCE.md`, Adenda 2).

## Comprobación de fuga por ids vecinos (ruta anterior de 320, test)
Con un id vecino a distancia 1 en train (746 imágenes): recall 0,804, F1 0,830. Sin vecino (144 imágenes): recall 0,880, F1 0,894. Las imágenes con vecino no rinden mejor (diferencia de recall IC95 −0,150 a +0,031): sin señal de fuga. Límite: a distancia 2, 870 de 890 imágenes tienen vecino, así que ese corte casi no tiene poder.

## Criterios de ACCEPTANCE.md
Tabla completa en `ACCEPTANCE.md`. Ruta final: A1 cumplido en la estimación puntual (recall 0,874; media de semillas 0,858; el IC baja a 0,828 y una semilla da 0,845); A2–A6 cumplidos (A5 con poco margen, +7,8 %). **Ruta anterior (320 sin borde): A1 (0,819) y A4 (−3,2 pts) no cumplidos.**

## Limitaciones
- **Un solo hospital**, muestras respiratorias de flora mixta con células y moco; no cultivos puros.
- **G-bacilli es multiespecie**, no solo E. coli.
- **Cajas casi cuadradas** (marcan la ubicación, no la forma): mAP50-95 no es informativo.
- **Sin fondos reales**: toda imagen tiene al menos una caja. Los fondos sintéticos se descartaron porque más del 5 % de las ventanas "vacías" contenía bacterias sin etiquetar.
- **Etiquetado no exhaustivo**: precisión y MAE están subestimados; parte de los falsos positivos son bacterias reales sin caja.
- **Baja densidad** (mediana 1 caja por imagen, máximo 17): el MAE global dice poco sobre campos densos; en el cuartil más denso el recall era ~0,72 (ruta anterior).
- **Leakage residual** posible en las 1918 imágenes "simples" (ids únicos que pueden compartir paciente o lámina) y entre ids consecutivos.
- **Resolución y magnificación desconocidas**; el celular acoplado al ocular será otro dominio. Las imágenes del dataset son borrosas y oscuras: el color rosado no se pudo verificar como señal de clase.
- Células pequeñas (25–43 px a 640) con recall ~0,69 en la ruta anterior.
- La decisión de la ruta con borde nació de comparar cifras de test (aunque se decidió con val).
- **Sin validación clínica.**
