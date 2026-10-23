#!/usr/bin/env python
"""Referencia para la app: corre el .tflite de MicaTB (G-bacilli) con el mismo preprocesado,
decodificacion y NMS que debe hacer la app. Dependencias: numpy, opencv-python y ai-edge-litert
(o tflite-runtime / tensorflow). No usa ultralytics.

Uso: python inferir_tflite.py --model ecoli_gb_E3s43_fp16.tflite --config app_config.json --image campo.jpg [--definicion estricta|amplia]
Salida (JSON): conteo y cajas [x1,y1,x2,y2] en pixeles de la imagen original, con clase y confianza.
"""
import argparse, json
import cv2
import numpy as np


def cargar_interprete(path):
    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        try:
            from tflite_runtime.interpreter import Interpreter
        except ImportError:
            from tensorflow.lite import Interpreter
    it = Interpreter(model_path=path); it.allocate_tensors()
    return it


def letterbox(bgr, size, fill=114):
    """Escala al lado mayor = size y rellena con gris centrado (igual que ultralytics, auto=False)."""
    h, w = bgr.shape[:2]
    r = min(size / h, size / w)
    nw, nh = round(w * r), round(h * r)
    dw, dh = (size - nw) / 2, (size - nh) / 2
    if (w, h) != (nw, nh):
        bgr = cv2.resize(bgr, (nw, nh), interpolation=cv2.INTER_LINEAR)
    top, bottom = round(dh - 0.1), round(dh + 0.1)
    left, right = round(dw - 0.1), round(dw + 0.1)
    out = cv2.copyMakeBorder(bgr, top, bottom, left, right, cv2.BORDER_CONSTANT, value=(fill, fill, fill))
    return out, r, left, top


def preprocesar(bgr, cfg):
    """Letterbox a `contenido` px y borde gris de (tamano - contenido)/2 px por lado -> entrada tamano x tamano."""
    size = cfg["entrada"]["tamano"]; cont = cfg["entrada"].get("contenido", size); borde = (size - cont) // 2
    img, r, left, top = letterbox(bgr, cont)
    if borde:
        img = cv2.copyMakeBorder(img, borde, borde, borde, borde, cv2.BORDER_CONSTANT, value=(114, 114, 114))
    x = cv2.cvtColor(img, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0   # RGB, 0..1
    return np.transpose(x, (2, 0, 1))[None], r, left + borde, top + borde   # NCHW


def nms_por_clase(boxes, scores, classes, iou_thr):
    """NMS greedy independiente por clase (suprime si IoU > umbral), como torchvision.batched_nms."""
    keep = []
    for c in np.unique(classes):
        idx = np.where(classes == c)[0]; idx = idx[np.argsort(-scores[idx])]
        while len(idx):
            i = idx[0]; keep.append(i)
            if len(idx) == 1: break
            rest = idx[1:]
            xx1 = np.maximum(boxes[i, 0], boxes[rest, 0]); yy1 = np.maximum(boxes[i, 1], boxes[rest, 1])
            xx2 = np.minimum(boxes[i, 2], boxes[rest, 2]); yy2 = np.minimum(boxes[i, 3], boxes[rest, 3])
            inter = np.clip(xx2 - xx1, 0, None) * np.clip(yy2 - yy1, 0, None)
            a1 = (boxes[i, 2] - boxes[i, 0]) * (boxes[i, 3] - boxes[i, 1])
            a2 = (boxes[rest, 2] - boxes[rest, 0]) * (boxes[rest, 3] - boxes[rest, 1])
            idx = rest[inter / (a1 + a2 - inter + 1e-9) <= iou_thr]
    return np.array(keep, dtype=int)


def detectar(it, bgr, cfg, definicion="estricta"):
    size = cfg["entrada"]["tamano"]; H, W = bgr.shape[:2]
    x, r, left, top = preprocesar(bgr, cfg)
    i0, o0 = it.get_input_details()[0], it.get_output_details()[0]
    it.set_tensor(i0["index"], x); it.invoke()
    y = it.get_tensor(o0["index"])[0]                                   # [4 + n_clases, n_anclas]
    cx, cy, w, h = y[0] * size, y[1] * size, y[2] * size, y[3] * size   # cajas normalizadas 0..1 -> px de entrada
    boxes = np.stack([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], 1)
    scores_all = y[4:].T; cls = scores_all.argmax(1); conf = scores_all.max(1)
    d = cfg["definiciones"][definicion]
    m = conf > d["conf"]; boxes, conf, cls = boxes[m], conf[m], cls[m]
    k = nms_por_clase(boxes, conf, cls, d["iou_nms"])[: cfg["max_det"]]
    boxes, conf, cls = boxes[k], conf[k], cls[k]
    boxes[:, [0, 2]] = (boxes[:, [0, 2]] - left) / r; boxes[:, [1, 3]] = (boxes[:, [1, 3]] - top) / r   # deshacer letterbox
    boxes[:, [0, 2]] = boxes[:, [0, 2]].clip(0, W); boxes[:, [1, 3]] = boxes[:, [1, 3]].clip(0, H)
    clases_contadas = d["clases_contadas"]
    contar = np.isin(cls, clases_contadas)
    return dict(conteo=int(contar.sum()),
                detecciones=[dict(clase=cfg["clases"][int(c)], conf=round(float(s), 4), caja=[round(float(v), 1) for v in b])
                             for b, s, c in zip(boxes, conf, cls)])


if __name__ == "__main__":
    ap = argparse.ArgumentParser(); ap.add_argument("--model", required=True); ap.add_argument("--config", required=True)
    ap.add_argument("--image", required=True); ap.add_argument("--definicion", default="estricta", choices=["estricta", "amplia"])
    a = ap.parse_args(); cfg = json.load(open(a.config, encoding="utf8"))
    img = cv2.imread(a.image); assert img is not None, "no se pudo leer la imagen"
    print(json.dumps(detectar(cargar_interprete(a.model), img, cfg, a.definicion), indent=1, ensure_ascii=False))
