#!/usr/bin/env python
"""Referencia de inferencia de Mica TB: lo que la app Android debe reproducir.

Lee models/model_config.json (fuente unica), corre el .tflite con el mismo preprocesado, decodificacion y
NMS que la app, y escribe conteos y cajas. Dependencias: numpy, opencv-python, ai-edge-litert.

Una imagen:   python reference_infer.py --image campo.jpg [--variant fp16|int8] [--definition strict|broad]
Una carpeta:  python reference_infer.py --dir test_vectors --out test_vectors/expected.json

Flujo (igual que la app): se ejecuta a base_conf, se hace NMS por clase y luego se filtra por el conf de
cada definicion de conteo (comparacion estricta `score > conf`). Filtrar despues del NMS da el mismo
resultado que filtrar antes, porque el NMS voraz por clase solo deja que cajas de mayor puntaje
supriman a las de menor puntaje.
"""
import argparse, hashlib, json
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONFIG = ROOT / "models" / "model_config.json"
IMAGE_EXT = {".jpg", ".jpeg", ".png"}


def load_interpreter(path):
    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        try:
            from tflite_runtime.interpreter import Interpreter
        except ImportError:
            from tensorflow.lite import Interpreter
    it = Interpreter(model_path=str(path))
    it.allocate_tensors()
    return it


def letterbox(bgr, size, fill=114):
    """Escala el lado mayor a `size` y rellena con gris centrado (como ultralytics, auto=False)."""
    h, w = bgr.shape[:2]
    r = min(size / h, size / w)
    nw, nh = round(w * r), round(h * r)
    dw, dh = (size - nw) / 2, (size - nh) / 2
    if (w, h) != (nw, nh):
        bgr = cv2.resize(bgr, (nw, nh), interpolation=cv2.INTER_LINEAR)
    top, bottom = round(dh - 0.1), round(dh + 0.1)
    left, right = round(dw - 0.1), round(dw + 0.1)
    out = cv2.copyMakeBorder(bgr, top, bottom, left, right, cv2.BORDER_CONSTANT, value=(fill,) * 3)
    return out, r, left, top


def preprocess(bgr, inp):
    """Letterbox a `content` px mas `border` px de gris por lado -> tensor NCHW float32 en RGB 0..1."""
    img, r, left, top = letterbox(bgr, inp["content"], inp["pad_value"])
    b = inp["border"]
    if b:
        img = cv2.copyMakeBorder(img, b, b, b, b, cv2.BORDER_CONSTANT, value=(inp["pad_value"],) * 3)
    x = cv2.cvtColor(img, cv2.COLOR_BGR2RGB).astype(np.float32) * inp["scale"]
    return np.transpose(x, (2, 0, 1))[None], r, left + b, top + b


def nms_per_class(boxes, scores, classes, iou_thr):
    """NMS voraz independiente por clase (suprime si IoU > umbral), como torchvision.batched_nms."""
    keep = []
    for c in np.unique(classes):
        idx = np.where(classes == c)[0]
        idx = idx[np.argsort(-scores[idx])]
        while len(idx):
            i = idx[0]
            keep.append(i)
            if len(idx) == 1:
                break
            rest = idx[1:]
            xx1 = np.maximum(boxes[i, 0], boxes[rest, 0]); yy1 = np.maximum(boxes[i, 1], boxes[rest, 1])
            xx2 = np.minimum(boxes[i, 2], boxes[rest, 2]); yy2 = np.minimum(boxes[i, 3], boxes[rest, 3])
            inter = np.clip(xx2 - xx1, 0, None) * np.clip(yy2 - yy1, 0, None)
            a1 = (boxes[i, 2] - boxes[i, 0]) * (boxes[i, 3] - boxes[i, 1])
            a2 = (boxes[rest, 2] - boxes[rest, 0]) * (boxes[rest, 3] - boxes[rest, 1])
            idx = rest[inter / (a1 + a2 - inter + 1e-9) <= iou_thr]
    return np.array(keep, dtype=int)


def detect(it, bgr, cfg):
    """Detecciones tras NMS a base_conf, en pixeles de la imagen original. Sin filtrar por conf final."""
    inp, post = cfg["input"], cfg["postprocess"]
    size = inp["width"]
    H, W = bgr.shape[:2]
    x, r, left, top = preprocess(bgr, inp)
    i0, o0 = it.get_input_details()[0], it.get_output_details()[0]
    it.set_tensor(i0["index"], x)
    it.invoke()
    y = it.get_tensor(o0["index"])[0]                                   # [4 + n_clases, n_anclas]
    cx, cy, w, h = y[0] * size, y[1] * size, y[2] * size, y[3] * size   # normalizado -> px de entrada
    boxes = np.stack([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], 1)
    scores_all = y[4:].T
    cls, conf = scores_all.argmax(1), scores_all.max(1)
    m = conf > post["base_conf"]
    boxes, conf, cls = boxes[m], conf[m], cls[m]
    k = nms_per_class(boxes, conf, cls, post["nms_iou"])
    k = k[np.argsort(-conf[k], kind="stable")][: post["max_det"]]       # max_det por puntaje global
    boxes, conf, cls = boxes[k], conf[k], cls[k]
    boxes[:, [0, 2]] = ((boxes[:, [0, 2]] - left) / r).clip(0, W)       # deshacer letterbox y borde
    boxes[:, [1, 3]] = ((boxes[:, [1, 3]] - top) / r).clip(0, H)
    return [dict(cls=int(c), **{"class": cfg["classes"][int(c)]}, score=round(float(s), 4),
                 box=[round(float(v), 1) for v in b]) for b, s, c in zip(boxes, conf, cls)]


def count(dets, definition):
    """Cuenta detecciones de las clases de la definicion con score > conf de esa definicion."""
    names = set(definition["classes"])
    return sum(1 for d in dets if d["class"] in names and d["score"] > definition["conf"])


def run(cfg, model_file, images):
    it = load_interpreter(model_file)
    out = {}
    for p in images:
        bgr = cv2.imread(str(p))
        assert bgr is not None, f"no se pudo leer {p}"
        dets = detect(it, bgr, cfg)
        out[p.name] = dict(width=bgr.shape[1], height=bgr.shape[0],
                           count_strict=count(dets, cfg["counting"]["strict"]),
                           count_broad=count(dets, cfg["counting"]["broad"]),
                           detections=dets)
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", default=str(DEFAULT_CONFIG))
    ap.add_argument("--variant", default="fp16", choices=["fp16", "int8"])
    ap.add_argument("--image")
    ap.add_argument("--dir")
    ap.add_argument("--out", help="archivo JSON de salida (por defecto, stdout)")
    ap.add_argument("--definition", default=None, choices=["strict", "broad"],
                    help="con --image, imprime solo las detecciones de esa definicion")
    a = ap.parse_args()
    assert bool(a.image) != bool(a.dir), "usa --image o --dir"

    cfg_path = Path(a.config)
    cfg = json.loads(cfg_path.read_text(encoding="utf8"))
    spec = cfg if a.variant == "fp16" else cfg["variants"][a.variant]
    model_file = cfg_path.parent / spec["file"]
    sha = hashlib.sha256(model_file.read_bytes()).hexdigest()
    assert sha == spec["sha256"], f"sha256 distinto en {model_file.name}: {sha}"

    images = [Path(a.image)] if a.image else sorted(p for p in Path(a.dir).iterdir() if p.suffix.lower() in IMAGE_EXT)
    result = dict(model_id=cfg["model_id"], variant=a.variant, sha256=sha,
                  base_conf=cfg["postprocess"]["base_conf"], images=run(cfg, model_file, images))
    if a.image and a.definition:
        d = cfg["counting"][a.definition]
        res = result["images"][images[0].name]
        res["detections"] = [x for x in res["detections"] if x["class"] in d["classes"] and x["score"] > d["conf"]]
    text = json.dumps(result, indent=1, ensure_ascii=False)
    if a.out:
        Path(a.out).write_text(text, encoding="utf8")
        print(f"{len(images)} imagenes -> {a.out}")
    else:
        print(text)
