# Guion de la demo en vivo (E. coli, láminas Gram fijadas, bioseguridad nivel 1)

Objetivo: mostrar el flujo completo del celular (escaneo en serpentín de ~100 campos → detector YOLOv8n sin internet → conteo por campo) con E. coli. **Es una demostración de ingeniería, no un resultado clínico ni un diagnóstico.** El modelo de TB es otro pipeline y se valida solo con imágenes públicas.

## Qué decir al inicio (30 s)
1. "Este detector cuenta bacilos Gram negativos (rosados) por campo, en el celular y sin internet."
2. "Se entrenó con un dataset público de otro dominio (muestras respiratorias de un hospital). Todavía no lo hemos validado con nuestras láminas: eso es la fase 2."
3. "Lo que vamos a ver es el flujo completo; los números en el celular son una referencia, no una medición clínica."

## Antes de empezar (checklist)
- [ ] Lámina de E. coli con tinción Gram **fijada**, ya montada; una lámina con poco material y otra más densa si es posible.
- [ ] Celular acoplado al ocular, enfoque fino probado; iluminación fija (no cambiar el brillo a mitad de la demo).
- [ ] App con el modelo cargado (INT8 o FP16 de `deploy_notes.md`, con el borde de 16 px en el preprocesado), definición de conteo **estricta** (solo G-bacilli) y umbrales de `app_config.json`.
- [ ] Modo avión activado: demostrar que corre sin internet.
- [ ] Un campo ya contado a mano por ti, para comparar en vivo.

## Secuencia (≈ 5–7 min)
1. **Campo único (1 min).** Enfoca un campo con pocas bacterias. Muestra la imagen con las cajas y el conteo. Cuéntalas tú en voz alta y compara.
2. **Campo denso (1 min).** Cambia a un campo con muchas células. Di claramente: "aquí es donde el modelo tiende a perder bacterias, sobre todo si están en contacto". Muestra el recall más bajo; no lo ocultes.
3. **Campo vacío o casi vacío (30 s).** Muestra que no inventa bacterias. Si aparece alguna caja, explica que puede ser un artefacto o una bacteria real.
4. **Escaneo en serpentín (2–3 min).** Recorre ~100 campos. Muestra el contador por campo y el total acumulado. Señala la latencia por cuadro en pantalla (objetivo < 150 ms).
5. **Comparación manual (1 min).** Cuenta a mano 2–3 campos y compara con la app. Reporta honestamente las diferencias.

## Si preguntan (respuestas honestas)
- **¿Es preciso?** En imágenes públicas de test: precisión 0,90 y recall 0,87 (IC 0,83–0,92) para bacilos Gram negativos, con error medio de conteo de 0,19 bacterias por imagen. Esos números son del dataset público; en nuestras láminas aún no están medidos.
- **¿Detecta E. coli específicamente?** No. Detecta bacilos Gram negativos en general (varias especies).
- **¿Sirve para diagnosticar?** No. No es un dispositivo de diagnóstico ni tiene validación clínica.
- **¿Y si el E. coli sale violáceo?** Hay una definición de conteo amplia (G− + G+ bacilos) lista como plan B.
- **¿Por qué falla en campos densos?** El dataset de entrenamiento tiene mediana de 1 bacteria por imagen; un cultivo puro tiene muchas más. Por eso la fase 2 captura campos densos.
- **¿Cuántas imágenes propias?** Ninguna aún; esa es la siguiente etapa.

## Qué NO hacer
- No presentar números del dataset público como resultados de las láminas propias.
- No afirmar que el modelo "identifica E. coli" ni que "diagnostica infección".
- No ajustar umbrales en vivo para que el conteo cuadre.
- No omitir los campos donde falla: son parte de la historia y justifican la fase 2.

## Plan B si algo falla
- Latencia alta en el celular: bajar a la ruta de respaldo (416 px) o mostrar el script de referencia en el portátil.
- Mala iluminación o desenfoque: re-enfocar y repetir el campo; si persiste, usar las imágenes de `casos_prueba/` para mostrar el comportamiento esperado.
- App no responde: ejecutar `inferir_tflite.py` sobre una foto del campo y mostrar la salida.
