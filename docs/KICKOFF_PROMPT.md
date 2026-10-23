# Cómo usar este paquete con Claude Code

## 1. Estructura de archivos antes de empezar

Crea la carpeta del proyecto (por ejemplo MicaTB/) y coloca:

```text
MicaTB/
  CLAUDE.md                  contexto permanente (en la raíz)
  docs/
    SPEC.md
    MODEL_CONTRACT.md
    BACKLOG.md
    ref/                     resultados del entrenamiento, solo lectura
      MODEL_CARD.md
      ACCEPTANCE.md
      RESUMEN.md
      EDA_DECISIONES.md
  models/
    ecoli_gb_E3s43_fp16.tflite
    (versión INT8 y respaldo de 416 px)
    model_config.json        si ya lo tienes; si no, lo crea MT-01
  reference/
    reference_infer.py       si ya lo tienes; si no, lo crea MT-01
    test_vectors/            10 a 20 imágenes con expected.json
    samples/                 imágenes de muestra, incluyendo campos vacíos y densos
```

Claude Code creará solo: docs/DECISIONS.md, PROGRESS.md y el proyecto Android.

Datos que te pediré o que debes tener a mano: modelo y versión de Android del celular, data.yaml o lista de clases con su índice del entrenamiento.

## 2. Herramientas recomendadas (opcionales pero útiles)

- Android Studio, JDK y depuración USB activada. Comprueba con adb devices.
- Servidor MCP mobile-mcp (verificado): deja que Claude Code vea capturas y toque la pantalla del celular real por adb, útil para comprobar la interfaz. Requiere Node 20 o superior.
  claude mcp add mobile-mcp -- npx -y @mobilenext/mobile-mcp@latest
- Servidor MCP Context7: documentación actualizada de librerías para que no use versiones de memoria. Revisa su comando de instalación actual en su documentación oficial antes de añadirlo.
- Skills de Android (de terceros, repositorio new-silvermoon/awesome-android-agent-skills, licencia Apache 2.0): se copian a .claude/skills/. Instala solo las que usarás: compose-ui, compose-navigation, android-testing, kotlin-concurrency-expert, android-data-layer, android-viewmodel. Lee cada SKILL.md antes de instalarla, porque son instrucciones que el agente seguirá.
- Modo plan de Claude Code para las issues que toquen arquitectura. Usa /clear entre issues.

Archivo opcional .claude/settings.json para no aprobar a mano los comandos de rutina (ajusta a tu sistema):

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew:*)",
      "Bash(gradlew.bat:*)",
      "Bash(adb devices)",
      "Bash(adb install:*)",
      "Bash(adb logcat:*)",
      "Bash(git status)",
      "Bash(git diff:*)",
      "Bash(git add:*)",
      "Bash(git commit:*)"
    ]
  }
}
```

## 3. Prompt de arranque (pégalo en la primera sesión)

```text
Vas a ayudarme a construir Mica TB, una app Android offline para apoyo al
tamizaje microscópico (contexto completo en CLAUDE.md). Trabajaremos por
fases con compuertas verificables. La prioridad es que funcione la cámara
con la IA en el celular real; el hardware motorizado va detrás de un
simulador.

Antes de escribir código:
1. Lee CLAUDE.md, docs/SPEC.md, docs/MODEL_CONTRACT.md y docs/BACKLOG.md.
   Lee también docs/ref/ si existe.
2. Revisa el contenido de models/ y reference/, y el entorno (JDK, SDK de
   Android, adb, y si hay un celular conectado con adb devices).
3. Dime qué entradas de la sección 3 de docs/MODEL_CONTRACT.md ya existen y
   cuáles faltan. Si falta algo bloqueante, pídemelo antes de seguir.
4. Entra en modo plan y propón el plan de las issues MT-01 y MT-02 (Fase 0),
   con el orden de trabajo, los comandos de verificación que usarás y los
   riesgos. Espera mi aprobación.

Reglas para toda la sesión:
- Una issue por vez, en el orden de docs/BACKLOG.md.
- No declares terminada una issue sin pegar la salida del comando de prueba
  o la evidencia en el dispositivo.
- No asumas nada sobre el modelo: mide y compara con reference/.
- Verifica versiones y APIs con documentación oficial antes de añadir una
  dependencia (en especial LiteRT: confirma si la versión elegida usa la
  API Interpreter o CompiledModel y qué implica para la GPU).
- Sin permiso INTERNET, sin OpenCV, sin Hilt, sin multi-módulo.
- Al terminar cada issue: actualiza PROGRESS.md, anota las decisiones en
  docs/DECISIONS.md y haz commit.
- No pases a MT-03 ni a nada posterior sin mi confirmación.

Responde en español, breve, sin guiones largos ni negritas.
```

## 4. Prompt para cada issue siguiente

```text
Trabaja la issue MT-0X de docs/BACKLOG.md. Lee su sección y las secciones
de docs/SPEC.md y docs/MODEL_CONTRACT.md que correspondan. Propón un plan
corto y espera mi OK si toca arquitectura o dependencias. Al terminar,
muestra la evidencia de los criterios de aceptación, actualiza PROGRESS.md
y haz commit.
```

## 5. Prompt para retomar tras cerrar la sesión

```text
Lee CLAUDE.md y PROGRESS.md. Resume en 5 líneas dónde estamos y cuál es la
siguiente issue según docs/BACKLOG.md. No empieces a programar hasta que
yo confirme.
```

## 6. Qué dejar en paralelo con otros agentes (si usas varios)

Se pueden hacer en paralelo, en ramas separadas, porque no comparten archivos: MT-06 (cámara) y MT-07 (calidad) con MT-03 y MT-04 (detector). Después de MT-04 y MT-06 hay que integrar de forma secuencial (MT-08). El protocolo BLE y su simulador (MT-15) pueden desarrollarse en paralelo con la Fase 2 una vez acordado el protocolo con el responsable del firmware.

Ramas sugeridas: main estable, una rama por issue (mt-04-detector), merge solo con pruebas en verde y evidencia en el celular cuando aplique.
