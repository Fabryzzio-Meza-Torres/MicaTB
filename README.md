# Mica TB

App Android offline de apoyo al tamizaje microscópico. El celular se acopla al ocular de un microscopio, captura campos, un detector YOLOv8n en TFLite cuenta bacilos y la app consolida los resultados para revisión por una persona especializada.

No es un dispositivo de diagnóstico. La demo usa E. coli en láminas Gram fijadas (bioseguridad nivel 1); el modelo de TB vendrá después.

Equipo: UTEC (computación, bioingeniería, mecatrónica).

## Estructura

| Carpeta | Contenido |
|---|---|
| `app/` | App Android (Kotlin, Compose, Room, CameraX, LiteRT). |
| `models/` | Modelos TFLite y `model_config.json`, fuente de verdad del contrato del modelo. |
| `reference/` | Script de Python de referencia y vectores de prueba para la paridad con la app. |
| `training/` | Notebooks de Kaggle, scripts, tablas y pesos del entrenamiento. |
| `docs/` | Especificación, contrato del modelo, backlog, decisiones y resultados del entrenamiento (`docs/ref/`). |

Estado del trabajo y lo pendiente: `PROGRESS.md`. Sistema visual: `DESIGN.md`. Producto: `PRODUCT.md`. Reglas de trabajo con el agente: `CLAUDE.md`.

## Modelo

YOLOv8n ajustado desde COCO, entrenado con el Clinical Bacteria DataSet (CC BY 4.0). Entrada 352x352 (letterbox a 320 más 16 px de borde gris), clases Gram, conteo principal de G-bacilli. Métricas y límites en `docs/ref/MODEL_CARD.md`.

Los archivos `.tflite`, `.pt` y `.onnx` están en Git LFS: instala `git lfs` antes de clonar.

## Referencia en Python

```bash
python -m venv reference/.venv
reference/.venv/Scripts/pip install -r reference/requirements.txt
reference/.venv/Scripts/python reference/reference_infer.py --help
```

## Compilar y probar

Requiere Android Studio (JDK incluido) y el SDK 37.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest   # con un emulador o un celular conectado
```n
Se necesita un archivo `local.properties` con `sdk.dir` (no se sube al repositorio).

## Licencia

Ver `LICENSE`. Los datos de entrenamiento tienen su propia licencia (CC BY 4.0). La tipografía Barlow se distribuye con licencia OFL (`docs/licenses/OFL-Barlow.txt`).