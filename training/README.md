# Entrenamiento del detector (E. coli, Gram)

Material para reproducir y auditar el modelo que usa la app. La app solo necesita `models/` y `reference/`.

Modelo final: YOLOv8n entrenado a 320 con 16 px de borde gris en inferencia (entrada 352), clase contada G-bacilli. Datos: Clinical Bacteria DataSet (CC BY 4.0). Resultados completos y límites en `docs/ref/`.

Es un detector de bacterias Gram para la demo con E. coli. No es un detector de TB ni un dispositivo de diagnóstico.

## Contenido

- `notebooks/`: notebooks de Kaggle tal como se enviaron (sin ejecutar). Tabla abajo.
- `src/`: scripts `k*.py` que generan los notebooks (`build_nb.py`) y la librería común `eclib.py`.
- `results/`: tablas, métricas, configuración, manifiesto del split y sumas de verificación.
- `weights/`: `.pt`, `.onnx` y TFLite FP32 del modelo final, para reproducir exportaciones (Git LFS).

## Notebooks de Kaggle

| Carpeta | Kernel en Kaggle | Archivo |
|---|---|---|
| k1 | fabryzziomezatorres/ecoli-datos-v2 | notebooks/ecoli-datos-v2.ipynb |
| k1b | fabryzziomezatorres/ecoli-clases-640 | notebooks/ecoli-clases-640.ipynb |
| k2 | fabryzziomezatorres/ecoli-e0-smoke2 | notebooks/ecoli-e0-smoke2.ipynb |
| k3 | fabryzziomezatorres/ecoli-e1-640 | notebooks/ecoli-e1-640.ipynb |
| k4 | fabryzziomezatorres/ecoli-e2e3 | notebooks/ecoli-e2e3.ipynb |
| k5 | fabryzziomezatorres/ecoli-export-test | notebooks/ecoli-export-test.ipynb |
| k6 | fabryzziomezatorres/ecoli-export-fp16 | notebooks/ecoli-export-fp16.ipynb |
| k7 | fabryzziomezatorres/ecoli-e4-hsv0 | notebooks/ecoli-e4-hsv0.ipynb |
| k8 | fabryzziomezatorres/ecoli-e3-s43 | notebooks/ecoli-e3-s43.ipynb |
| k9 | fabryzziomezatorres/ecoli-e3-s44 | notebooks/ecoli-e3-s44.ipynb |
| k10 | fabryzziomezatorres/ecoli-analisis-e3 | notebooks/ecoli-analisis-e3.ipynb |
| k11 | fabryzziomezatorres/ecoli-final | notebooks/ecoli-final.ipynb |
| k12 | fabryzziomezatorres/ecoli-export-final | notebooks/ecoli-export-final.ipynb |
| k13 | fabryzziomezatorres/ecoli-verify-io | notebooks/ecoli-verify-io.ipynb |
| k14 | fabryzziomezatorres/ecoli-verificaciones | notebooks/ecoli-verificaciones.ipynb |
| k15 | fabryzziomezatorres/ecoli-interp | notebooks/ecoli-interp.ipynb |
| k16 | fabryzziomezatorres/ecoli-pad352 | notebooks/ecoli-pad352.ipynb |
| k17 | fabryzziomezatorres/ecoli-final-pad352 | notebooks/ecoli-final-pad352.ipynb |
| k18 | fabryzziomezatorres/ecoli-geom | notebooks/ecoli-geom.ipynb |

El modelo entregado sale de `ecoli-final-pad352` (k17). La ruta anterior sin borde (k11, k12) se conserva solo como historial.

## Reproducir

Cada notebook se sube con `kaggle kernels push` desde una carpeta con su `kernel-metadata.json`. Las dependencias entre kernels (`kernel_sources`) y el dataset de origen están descritos en los notebooks.