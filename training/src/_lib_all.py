import os, json, time, shutil, subprocess, sys
from pathlib import Path
import numpy as np, pandas as pd, cv2

CLASS_NAMES = ["G-cocci", "G+cocci", "G-bacilli", "G+bacilli"]
GB, GPB = 2, 3          # ids de G-bacilli y G+bacilli
NMS_IOU = 0.7

def prepare_ds(src_root, dst_root="/kaggle/temp/yolo_ds", with_bg=False):
    """Copia el dataset a un lugar escribible y escribe el yaml con rutas reales."""
    src_root, dst_root = Path(src_root), Path(dst_root)
    if not dst_root.exists(): shutil.copytree(src_root, dst_root)
    names = "\n".join(f"  {i}: {n}" for i, n in enumerate(CLASS_NAMES))
    tr = "[images/train, images/bg_train]" if with_bg else "images/train"
    y = dst_root / ("data_bg.yaml" if with_bg else "data_base.yaml")
    y.write_text(f"path: {dst_root}\ntrain: {tr}\nval: images/val\ntest: images/test\nnames:\n{names}\n")
    return dst_root, y

def read_gt(label_path):
    a = np.loadtxt(label_path, ndmin=2) if Path(label_path).stat().st_size else np.zeros((0, 5))
    c = a[:, 0].astype(int); xc, yc, w, h = a[:, 1:5].T
    return c, np.stack([xc-w/2, yc-h/2, xc+w/2, yc+h/2], 1)

def predict_dets(model, img_paths, imgsz, conf=0.01, iou=NMS_IOU, batch=32, device=None, max_det=1000):
    """Detecciones de baja confianza; el barrido de umbral se hace despues sobre este cache."""
    out = {}
    paths = [str(p) for p in img_paths]
    for i in range(0, len(paths), batch):
        for p, r in zip(paths[i:i+batch], model.predict(paths[i:i+batch], imgsz=imgsz, conf=conf, iou=iou, verbose=False, device=device, max_det=max_det)):
            h, w = r.orig_shape
            b = r.boxes
            out[Path(p).stem] = (b.cls.cpu().numpy().astype(int), b.conf.cpu().numpy(), b.xyxy.cpu().numpy() / np.array([w, h, w, h]))
    return out

def box_iou(a, b):
    if len(a) == 0 or len(b) == 0: return np.zeros((len(a), len(b)))
    iw = np.clip(np.minimum(a[:, None, 2], b[None, :, 2]) - np.maximum(a[:, None, 0], b[None, :, 0]), 0, None)
    ih = np.clip(np.minimum(a[:, None, 3], b[None, :, 3]) - np.maximum(a[:, None, 1], b[None, :, 1]), 0, None)
    i = iw * ih; ua = ((a[:, 2]-a[:, 0])*(a[:, 3]-a[:, 1]))[:, None] + ((b[:, 2]-b[:, 0])*(b[:, 3]-b[:, 1]))[None] - i
    return i / np.maximum(ua, 1e-9)

def load_gts(ds_root, split):
    return {p.stem: read_gt(p) for p in sorted((Path(ds_root) / "labels" / split).glob("*.txt"))}

def counts(dets, gts, conf, classes):
    """Conteos por imagen (gt, pred) de las clases dadas."""
    rows = []
    for k, (gc, _) in gts.items():
        c, s, _b = dets[k]
        rows.append((int(np.isin(gc, classes).sum()), int((np.isin(c, classes) & (s >= conf)).sum())))
    return np.array(rows)

def count_metrics(dets, gts, conf, classes):
    a = counts(dets, gts, conf, classes); e = a[:, 1] - a[:, 0]
    return dict(mae=float(np.abs(e).mean()), bias=float(e.mean()), exact=float((e == 0).mean()),
                gt_total=int(a[:, 0].sum()), pred_total=int(a[:, 1].sum()))

def boot_ci(dets, gts, conf, classes, n=1000, seed=0):
    a = counts(dets, gts, conf, classes); e = np.abs(a[:, 1] - a[:, 0]); rng = np.random.default_rng(seed)
    m = [e[rng.integers(0, len(e), len(e))].mean() for _ in range(n)]
    return [float(np.percentile(m, 2.5)), float(np.percentile(m, 97.5))]

def boot_ci_groups(dets, gts, conf, classes, group_of, n=1000, seed=0, stat="mae"):
    """IC 95 % remuestreando GRUPOS de origen (group_of: stem -> grupo), no imagenes."""
    keys = list(gts); a = counts(dets, gts, conf, classes); e = np.abs(a[:, 1] - a[:, 0])
    g = np.array([group_of[k] for k in keys]); ug, inv = np.unique(g, return_inverse=True)
    sums = np.bincount(inv, weights=e, minlength=len(ug)); cnt = np.bincount(inv, minlength=len(ug)); rng = np.random.default_rng(seed)
    m = []
    for _ in range(n):
        ix = rng.integers(0, len(ug), len(ug)); m.append(sums[ix].sum() / cnt[ix].sum())
    return [float(np.percentile(m, 2.5)), float(np.percentile(m, 97.5))]

def match_stats(dets, gts, conf, cls=GB, thr=0.5):
    """TP/FP/FN de la clase cls (IoU>=thr, 1 a 1 greedy) y confusiones con la otra clase de bacilos."""
    tp = fp = fn = conf_to_gpb = 0
    for k, (gc, gbx) in gts.items():
        c, s, b = dets[k]; m = s >= conf
        pc, pb = c[m], b[m]
        g = gc == cls; gb_ = gbx[g]; p = pb[pc == cls]
        iou = box_iou(p, gb_); used_p, used_g = set(), set()
        for v, i, j in sorted(((iou[i, j], i, j) for i in range(len(p)) for j in range(len(gb_))), reverse=True):
            if v < thr: break
            if i in used_p or j in used_g: continue
            used_p.add(i); used_g.add(j)
        t = len(used_p); tp += t; fp += len(p) - t; fn += len(gb_) - t
        other = GPB if cls == GB else GB
        un = [j for j in range(len(gb_)) if j not in used_g]
        if un:
            q = box_iou(pb[pc == other], gb_[un]); conf_to_gpb += int((q >= thr).any(0).sum()) if q.size else 0
    P = tp / max(tp + fp, 1); R = tp / max(tp + fn, 1)
    return dict(tp=tp, fp=fp, fn=fn, precision=P, recall=R, f1=2*P*R/max(P+R, 1e-9), gt_clase_predicha_como_otra=conf_to_gpb)

def sweep(dets, gts, classes, confs=np.round(np.arange(0.05, 0.81, 0.05), 2)):
    rows = []
    for c in confs:
        r = dict(conf=float(c), **count_metrics(dets, gts, c, classes))
        r.update({"f1_Gb": match_stats(dets, gts, c)["f1"]}); rows.append(r)
    return pd.DataFrame(rows)

def bg_fp(dets_bg, conf, classes=(GB,)):
    n = np.array([int((np.isin(c, classes) & (s >= conf)).sum()) for c, s, _ in dets_bg.values()])
    return dict(n_imgs=len(n), fp_por_imagen=float(n.mean()), imgs_con_fp=float((n > 0).mean()))

def cpu_latency_ms(model, img, imgsz, n=100, warm=10):
    for _ in range(warm): model.predict(img, imgsz=imgsz, device="cpu", verbose=False)
    t = []
    for _ in range(n):
        t0 = time.perf_counter(); model.predict(img, imgsz=imgsz, device="cpu", verbose=False); t.append((time.perf_counter()-t0)*1000)
    return float(np.median(t))

def ap_table(m):
    """mAP50 y mAP50-95 por clase desde un objeto DetMetrics de ultralytics."""
    idx = list(m.box.ap_class_index)
    return {CLASS_NAMES[c]: dict(ap50=float(m.box.ap50[i]), ap=float(m.box.ap[i])) for i, c in enumerate(idx)}

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


