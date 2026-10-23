# Progreso

Estado vivo del trabajo. Última actualización: 2026-10-23.

## Fase 0

### MT-01 Inspección del modelo y contrato: hecho

Qué se hizo:
- Se inspeccionaron los .tflite FP16 e INT8: entrada [1,3,352,352] float32, salida [1,8,2541] float32, cajas normalizadas, puntajes activados, cuantización 0 en ambos.
- models/model_config.json con sha256 (coinciden con los del entrenamiento) y las dos definiciones de conteo.
- reference/reference_infer.py lee esa configuración, comprueba el hash y genera expected.json.
- reference/test_vectors/expected.json para las 23 imágenes. Comparación con los resultados de Kaggle en las 20 que los tienen: conteo estricto y amplio iguales en 20 de 20, IoU mínimo de cajas 0,984, diferencia máxima de puntaje 0,024.
- CLAUDE.md y docs/MODEL_CONTRACT.md actualizados a PAD352. Decisiones en docs/DECISIONS.md.

Qué falta o está abierto:
- Las 3 imágenes no cuadradas no tienen resultado independiente de Kaggle, solo el del script.
- La diferencia de puntaje de 0,024 supera la tolerancia de 0,01 del contrato (decisión abierta, ver DECISIONS.md).
- No se corrió el INT8 sobre los vectores de prueba.

Cómo probarlo:

```bash
python -m venv reference/.venv
reference/.venv/Scripts/pip install -r reference/requirements.txt
reference/.venv/Scripts/python reference/reference_infer.py --dir reference/test_vectors --out reference/test_vectors/expected.json
```

### MT-02 Proyecto Android base: pendiente

Bloqueado por el entorno de esta PC: solo hay Java 8; faltan Android Studio, SDK, JDK 17 y adb. El usuario instalará Android Studio y activará la depuración USB. Celular de pruebas: aún sin definir (se usa minSdk 26).

## Siguiente

1. Instalar Android Studio y confirmar con adb devices.
2. MT-02 según docs/BACKLOG.md.
3. No pasar a MT-03 sin confirmación.