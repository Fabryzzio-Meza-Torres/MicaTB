# %% [markdown]
# # Sensibilidad al redimensionado 640 -> 320 (INTER_LINEAR vs INTER_AREA). Se decide con VAL; test solo como diagnostico.
# %%
import json, shutil, subprocess
from pathlib import Path
import numpy as np, pandas as pd, cv2
subprocess.run("pip install -q ultralytics", shell=True)
from ultralytics import YOLO
from ultralytics.utils.metrics import compute_ap
OUT = Path("/kaggle/working/outputs"); OUT.mkdir(parents=True, exist_ok=True)
DSR = next(Path("/kaggle/input").glob("**/yolo_ds")); W = next(Path("/kaggle/input").glob("**/E3s43_best.pt")); m = YOLO(str(W))
IM = {s: sorted((DSR / "images" / s).glob("*.jpg")) for s in ("val", "test")}; GT = {s: load_gts(DSR, s) for s in ("val", "test")}
def run(paths, interp):
    out = {}
    for i in range(0, len(paths), 32):
        ch = paths[i:i + 32]; arr = [cv2.resize(cv2.imread(str(p)), (320, 320), interpolation=interp) for p in ch]
        for p, r in zip(ch, m.predict(arr, imgsz=320, conf=0.01, iou=0.5, verbose=False, device="cpu", max_det=1000)):
            b = r.boxes; out[p.stem] = (b.cls.cpu().numpy().astype(int), b.conf.cpu().numpy(), b.xyxy.cpu().numpy() / 320)
    return out
def ap50(dets, gts, cls=GB):
    rows, ngt = [], 0
    for k, (gc, gb) in gts.items():
        c, s, b = dets[k]; p = np.where(c == cls)[0]; g = np.where(gc == cls)[0]; ngt += len(g); order = p[np.argsort(-s[p])]; iou = box_iou(b[order], gb[g]); used = set()
        for i, pi in enumerate(order):
            best, bv = -1, 0.5
            for j in range(len(g)):
                if j not in used and iou[i, j] >= bv: best, bv = j, iou[i, j]
            if best >= 0: used.add(best)
            rows.append((s[pi], int(best >= 0)))
    rows.sort(key=lambda r: -r[0]); tp = np.cumsum([r[1] for r in rows]); fp = np.cumsum([1 - r[1] for r in rows]); return float(compute_ap(tp / max(ngt, 1), tp / np.maximum(tp + fp, 1))[0])
res = {}; V = {}
for name, interp in (("LINEAR", cv2.INTER_LINEAR), ("AREA", cv2.INTER_AREA)):
    dv = run(IM["val"], interp); sw = sweep(dv, GT["val"], [GB]); c = float(sw.loc[sw.mae.idxmin(), "conf"]); ms = match_stats(dv, GT["val"], c)
    dt = run(IM["test"], interp); mt = match_stats(dt, GT["test"], c); ct = count_metrics(dt, GT["test"], c, [GB])
    res[name] = dict(val=dict(conf_min_mae=c, ap50=ap50(dv, GT["val"]), mae=float(sw.mae.min()), precision=ms["precision"], recall=ms["recall"], f1=ms["f1"]),
                     test_diagnostico_con_conf_de_val=dict(ap50=ap50(dt, GT["test"]), mae=ct["mae"], precision=mt["precision"], recall=mt["recall"], f1=mt["f1"]))
    print(name, json.dumps(res[name], indent=1))
(OUT / "interpolacion.json").write_text(json.dumps(res, indent=1))
