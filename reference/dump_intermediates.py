#!/usr/bin/env python
"""Vuelca los intermedios de la referencia para probar el pre y postprocesado de la app en JVM, sin celular.

Por imagen escribe en test_vectors/intermediates/:
  <nombre>.input.u8   352x352x3 RGB uint8 (HWC), la entrada del modelo antes de multiplicar por scale
  <nombre>.raw.f32    salida cruda del modelo [8, 2541] float32 little-endian, canales primero

Uso: python dump_intermediates.py
"""
import json
from pathlib import Path

import cv2
import numpy as np

import reference_infer as ref

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "test_vectors" / "intermediates"
# cuadrada, no cuadrada y densa (16 bacilos)
IMAGES = ["000087_3_5.jpg", "nocuadrada_0_901021.jpg", "901699.jpg"]

cfg = json.loads((ROOT.parent / "models" / "model_config.json").read_text(encoding="utf8"))
inp = cfg["input"]
it = ref.load_interpreter(ROOT.parent / "models" / cfg["file"])
OUT.mkdir(exist_ok=True)

for name in IMAGES:
    bgr = cv2.imread(str(ROOT / "test_vectors" / name))
    img, _, _, _ = ref.letterbox(bgr, inp["content"], inp["pad_value"])
    b = inp["border"]
    img = cv2.copyMakeBorder(img, b, b, b, b, cv2.BORDER_CONSTANT, value=(inp["pad_value"],) * 3)
    rgb = np.ascontiguousarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
    (OUT / f"{name}.input.u8").write_bytes(rgb.tobytes())

    x, _, _, _ = ref.preprocess(bgr, inp)
    i0, o0 = it.get_input_details()[0], it.get_output_details()[0]
    it.set_tensor(i0["index"], x)
    it.invoke()
    y = np.ascontiguousarray(it.get_tensor(o0["index"])[0], dtype="<f4")
    assert y.shape == (8, 2541), y.shape
    (OUT / f"{name}.raw.f32").write_bytes(y.tobytes())
    print(name, bgr.shape, "->", rgb.shape, y.shape)
