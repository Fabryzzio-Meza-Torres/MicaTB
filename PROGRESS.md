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

### MT-02 Proyecto Android base: compila y pasa pruebas; falta probar en el celular

Qué se hizo:
- Entorno instalado en esta PC: Android Studio, command-line tools, SDK (plataformas 36 y 37.0, build-tools 36.0.0, platform-tools) y licencias aceptadas.
- Proyecto de un módulo (pe.utec.micatb, minSdk 26) con Compose, Material 3 y Navigation Compose, catálogo de versiones, wrapper de Gradle 9.6.0 y assets tomados de models/.
- Pantalla Home que lee model_config.json y muestra el id del modelo, el aviso "Apoyo al tamizaje, no diagnóstico" y la nota de dominio.
- Prueba JVM ModelConfigTest (2 pruebas) sobre el model_config.json real.

Evidencia (gradlew.bat test assembleDebug):
- BUILD SUCCESSFUL; ModelConfigTest: tests=2, failures=0, errors=0.
- app-debug.apk de 21,8 MB con assets/ecoli_gb_E3s43_352_fp16.tflite, ..._int8.tflite y model_config.json (sin comprimir los tflite).
- Permiso android.permission.INTERNET ausente en el manifiesto fusionado y en el APK (aapt2 dump permissions solo muestra el permiso interno de AndroidX).

Qué falta (criterio de aceptación incompleto):
- adb install y abrir la app mostrando el id del modelo: no hay celular conectado (adb devices vacío). Pendiente de que se conecte con depuración USB.
- Celular de pruebas aún sin definir.
- La compilación se hizo dentro de OneDrive y no hubo bloqueos de archivos en esta corrida; sigue siendo un riesgo si la sincronización interfiere.

Cómo probarlo:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```
## Siguiente

1. Conectar el celular (depuración USB), correr adb devices, instalar el APK y comprobar la pantalla Home. Con eso se cierra MT-02.
2. No pasar a MT-03 sin confirmación.
