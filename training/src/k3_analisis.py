# %% [markdown]
# # Pasos 5 y 6 (CPU): analisis de errores y umbrales, solo VAL. Test no se toca.
# MODEL y IMGSZ se inyectan. Modelo analizado: ver MODEL.

# %% [markdown]
# ## Entorno y datos
# %%
import os, json, pickle, subprocess, time
from pathlib import Path
import numpy as np, pandas as pd, cv2
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
subprocess.run("pip install -q ultralytics", shell=True)
from ultralytics import YOLO
OUT = Path("/kaggle/working/outputs"); OUT.mkdir(parents=True, exist_ok=True)
DSR = next(Path("/kaggle/input").glob("**/yolo_ds"))
W = next(Path("/kaggle/input").glob(f"**/{MODEL}_best.pt")); print("pesos:", W, "| datos:", DSR)
sp = pd.read_csv(DSR / "splits.csv", dtype={"dhash": str}); sp["stem"] = sp.name.str.replace(".jpg", "", regex=False)
INFO = sp.set_index("stem"); GROUP_OF = INFO.group.to_dict(); PATTERN = INFO.pattern.to_dict()
VAL_IMGS = sorted((DSR / "images" / "val").glob("*.jpg")); GTS = load_gts(DSR, "val")
model = YOLO(str(W))
t0 = time.time(); DETS = {0.7: predict_dets(model, VAL_IMGS, IMGSZ, conf=0.01, iou=0.7, device="cpu")}; print("inferencia val", round(time.time() - t0), "s")
sw = sweep(DETS[0.7], GTS, [GB]); CONF = float(sw.loc[sw.mae.idxmin(), "conf"]); print("conf provisional (min MAE, iou 0.7):", CONF)

# %% [markdown]
# ## Emparejamiento por caja (IoU>=0.5, 1 a 1 greedy) y matriz de confusion
# %%
def match_boxes(dets, gts, conf, cls=GB, thr=0.5):
    gt_rows, pr_rows = [], []
    for k, (gc, gbx) in gts.items():
        c, s, b = dets[k]; m = s >= conf; pc, pb, ps = c[m], b[m], s[m]
        g = np.where(gc == cls)[0]; p = np.where(pc == cls)[0]
        iou = box_iou(pb[p], gbx[g]); up, ug = set(), set()
        for v, i, j in sorted(((iou[i, j], i, j) for i in range(len(p)) for j in range(len(g))), reverse=True):
            if v < thr: break
            if i in up or j in ug: continue
            up.add(i); ug.add(j)
        for j, gi in enumerate(g):
            bb = gbx[gi]; gt_rows.append((k, float(min(bb[2] - bb[0], bb[3] - bb[1]) * 640), j in ug))
        for i, pi in enumerate(p): pr_rows.append((k, float(ps[pi]), i in up))
    return pd.DataFrame(gt_rows, columns=["stem", "size_px", "matched"]), pd.DataFrame(pr_rows, columns=["stem", "conf", "tp"])

def confusion(dets, gts, conf, thr=0.5):
    M = np.zeros((5, 5), int)   # filas GT (0..3, 4=fondo), columnas pred
    for k, (gc, gbx) in gts.items():
        c, s, b = dets[k]; m = s >= conf; pc, pb = c[m], b[m]
        iou = box_iou(gbx, pb); ug, up = set(), set()
        for v, i, j in sorted(((iou[i, j], i, j) for i in range(len(gc)) for j in range(len(pc))), reverse=True):
            if v < thr: break
            if i in ug or j in up: continue
            ug.add(i); up.add(j); M[gc[i], pc[j]] += 1
        for i in range(len(gc)):
            if i not in ug: M[gc[i], 4] += 1
        for j in range(len(pc)):
            if j not in up: M[4, pc[j]] += 1
    return pd.DataFrame(M, index=CLASS_NAMES + ["fondo"], columns=CLASS_NAMES + ["fondo"])
cm = confusion(DETS[0.7], GTS, CONF); cm.to_csv(OUT / "confusion_val.csv"); print(cm.to_string())
fig, ax = plt.subplots(figsize=(6, 5)); ax.imshow(np.log1p(cm.values), cmap="Blues"); ax.set_xticks(range(5)); ax.set_xticklabels(cm.columns, rotation=45); ax.set_yticks(range(5)); ax.set_yticklabels(cm.index)
for i in range(5):
    for j in range(5): ax.text(j, i, cm.values[i, j], ha="center", va="center", fontsize=8)
ax.set_xlabel("prediccion"); ax.set_ylabel("verdad"); plt.tight_layout(); plt.savefig(OUT / "confusion_val.png", dpi=80); plt.close()
perclass = json.loads(next(Path("/kaggle/input").glob(f"**/{MODEL}_metrics.json")).read_text())["per_class"]; print(json.dumps(perclass, indent=1))

# %% [markdown]
# ## Paso 5: G-bacilli por cuartil, crop vs simple, canario de similitud
# %%
gt_df, pr_df = match_boxes(DETS[0.7], GTS, CONF)
def prf(g, p):
    tp = int(g.matched.sum()); fn = len(g) - tp; fp = int((~p.tp).sum()); P = tp / max(tp + fp, 1); R = tp / max(tp + fn, 1)
    return dict(n_gt=len(g), tp=tp, fp=fp, fn=fn, precision=P, recall=R, f1=2 * P * R / max(P + R, 1e-9))
def subset_metrics(stems):
    st = set(stems); g = {k: v for k, v in GTS.items() if k in st}
    r = prf(gt_df[gt_df.stem.isin(st)], pr_df[pr_df.stem.isin(st)]); r.update(n_img=len(st), mae=count_metrics(DETS[0.7], g, CONF, [GB])["mae"]); return r
V = INFO.loc[[p.stem for p in VAL_IMGS]].copy(); V["n_gb"] = [int((GTS[s][0] == GB).sum()) for s in V.index]
rows = []
for pat in ("crop", "simple"): rows.append(dict(corte=f"patron={pat}", **subset_metrics(V.index[V.pattern == pat])))
for col in ("gray", "blur", "n_gb"):
    q = pd.qcut(V[col].rank(method="first"), 4, labels=["Q1", "Q2", "Q3", "Q4"])
    for lab in ("Q1", "Q2", "Q3", "Q4"): rows.append(dict(corte=f"{col} {lab} [{V[col][q == lab].min():.1f},{V[col][q == lab].max():.1f}]", **subset_metrics(V.index[q == lab])))
bins = pd.qcut(gt_df.size_px.rank(method="first"), 4, labels=["Q1", "Q2", "Q3", "Q4"])
for lab in ("Q1", "Q2", "Q3", "Q4"):
    g = gt_df[bins == lab]; rows.append(dict(corte=f"tamano caja (lado min px@640) {lab} [{g.size_px.min():.0f},{g.size_px.max():.0f}]", n_gt=len(g), recall=float(g.matched.mean())))
seg = pd.DataFrame(rows); seg.to_csv(OUT / "segmentos_val.csv", index=False); print(seg.round(3).to_string())
# canario: vecino mas parecido en train (dHash) y por histograma Lab
tr = INFO[INFO.split == "train"]; trh = tr.dhash.astype(np.uint64).to_numpy().view(np.uint8).reshape(-1, 8)
LUT = np.array([bin(i).count("1") for i in range(256)], np.uint8)
def hist(path):
    lab = cv2.cvtColor(cv2.imread(str(path)), cv2.COLOR_BGR2LAB); h = cv2.calcHist([lab], [0, 1, 2], None, [8, 8, 8], [0, 256] * 3).ravel(); return h / h.sum()
trH = np.stack([hist(DSR / "images" / "train" / n) for n in tr.name]); vH = np.stack([hist(DSR / "images" / "val" / f"{s}.jpg") for s in V.index])
vh = V.dhash.astype(np.uint64).to_numpy().view(np.uint8).reshape(-1, 8)
V["nn_dhash"] = [int(LUT[np.bitwise_xor(trh, h)].sum(1).min()) for h in vh]
V["nn_hist"] = [float(np.minimum(trH, h).sum(1).max()) for h in vH]
print("val: vecino train dHash min -> cuantiles", V.nn_dhash.quantile([0, .1, .5, .9, 1]).tolist(), "| hist-intersection cuantiles", V.nn_hist.quantile([0, .1, .5, .9, 1]).round(3).tolist())
rows = []
for col in ("nn_dhash", "nn_hist"):
    dec = pd.qcut(V[col].rank(method="first"), 10, labels=False)
    for d in range(10): rows.append(dict(medida=col, decil=d, rango=f"[{V[col][dec == d].min():.3f},{V[col][dec == d].max():.3f}]", **subset_metrics(V.index[dec == d])))
can = pd.DataFrame(rows); can.to_csv(OUT / "canario_val.csv", index=False); print(can.round(3).to_string())
print("corr(similitud, error por imagen):")
V["err"] = [abs(int(((DETS[0.7][s][0] == GB) & (DETS[0.7][s][1] >= CONF)).sum()) - V.n_gb[s]) for s in V.index]
print(V[["nn_dhash", "nn_hist", "err"]].corr(method="spearman").round(3).to_string())
V.drop(columns=["dhash"]).to_csv(OUT / "val_por_imagen.csv")

# %% [markdown]
# ## 24 peores imagenes (FP+FN de G-bacilli) con GT (verde) y predicciones (rojo, conf)
# %%
fpn = pr_df[~pr_df.tp].groupby("stem").size(); fnn = gt_df[~gt_df.matched].groupby("stem").size()
V["fp"] = fpn.reindex(V.index).fillna(0).astype(int); V["fn"] = fnn.reindex(V.index).fillna(0).astype(int); V["bad"] = V.fp + V.fn
print("val: imagenes con algun error G-bacilli:", int((V.bad > 0).sum()), "/", len(V), "| FP totales", int(V.fp.sum()), "FN totales", int(V.fn.sum()))
worst = V.sort_values("bad", ascending=False).head(24)
fig, axs = plt.subplots(4, 6, figsize=(30, 20))
for ax, (s, r) in zip(axs.ravel(), worst.iterrows()):
    im = cv2.cvtColor(cv2.imread(str(DSR / "images" / "val" / f"{s}.jpg")), cv2.COLOR_BGR2RGB); gc, gb = GTS[s]; c, sc, b = DETS[0.7][s]
    for cl, bb in zip(gc, gb):
        x1, y1, x2, y2 = (bb * 640).astype(int); cv2.rectangle(im, (x1, y1), (x2, y2), (0, 200, 0) if cl == GB else (255, 160, 0), 2)
    for cl, cf, bb in zip(c, sc, b):
        if cf >= CONF:
            x1, y1, x2, y2 = (bb * 640).astype(int); cv2.rectangle(im, (x1 - 3, y1 - 3), (x2 + 3, y2 + 3), (255, 0, 0) if cl == GB else (150, 0, 255), 2); cv2.putText(im, f"{CLASS_NAMES[cl][:4]}{cf:.2f}", (x1, max(y1 - 6, 10)), 0, 0.6, (255, 0, 0), 2)
    ax.imshow(im); ax.set_title(f"{s} FP={r.fp} FN={r.fn}", fontsize=14); ax.axis("off")
plt.suptitle("verde=GT G-bacilli, naranja=GT otra clase, rojo=pred G-bacilli, violeta=pred otra clase"); plt.tight_layout(); plt.savefig(OUT / "peores24.png", dpi=45); plt.close()
# FP confiables: fracción de FP con conf alta
print("FP por rango de conf:", pd.cut(pr_df[~pr_df.tp].conf, [0, .4, .6, .8, 1]).value_counts().sort_index().to_dict())

# %% [markdown]
# ## Paso 6: rejilla conf x IoU de NMS, definicion estricta y amplia (solo val)
# %%
for iou in (0.5, 0.6):
    DETS[iou] = predict_dets(model, VAL_IMGS, IMGSZ, conf=0.01, iou=iou, device="cpu")
confs = np.round(np.arange(0.05, 0.701, 0.05), 2); rows = []
for iou, d in DETS.items():
    for nm, cl in (("estricta", [GB]), ("amplia", [GB, GPB])):
        for c in confs:
            r = dict(iou_nms=iou, definicion=nm, conf=float(c), **count_metrics(d, GTS, c, cl))
            if nm == "estricta": r.update({k: v for k, v in match_stats(d, GTS, c).items() if k in ("precision", "recall", "f1")})
            rows.append(r)
grid = pd.DataFrame(rows); grid.to_csv(OUT / "rejilla_val.csv", index=False)
best = {}
for nm, cl in (("estricta", [GB]), ("amplia", [GB, GPB])):
    g = grid[grid.definicion == nm]; r = g.loc[g.mae.idxmin()]; best[nm] = dict(iou_nms=float(r.iou_nms), conf=float(r.conf), mae=float(r.mae), bias=float(r.bias))
    best[nm]["mae_ci95_grupos"] = boot_ci_groups(DETS[best[nm]["iou_nms"]], GTS, best[nm]["conf"], cl, GROUP_OF)
    if nm == "estricta":
        f = g.loc[g.f1.idxmax()]; best[nm]["optimo_F1"] = dict(iou_nms=float(f.iou_nms), conf=float(f.conf), f1=float(f.f1), precision=float(f.precision), recall=float(f.recall))
        ms = match_stats(DETS[best[nm]["iou_nms"]], GTS, best[nm]["conf"]); best[nm].update(precision=ms["precision"], recall=ms["recall"], f1=ms["f1"])
best["max_det"] = 1000; best["modelo"] = MODEL; best["imgsz"] = IMGSZ
(OUT / f"umbrales_{MODEL}_val.json").write_text(json.dumps(best, indent=1)); print(json.dumps(best, indent=1))
print(grid[(grid.definicion == "estricta") & (grid.iou_nms == 0.7)][["conf", "mae", "bias", "precision", "recall", "f1"]].round(3).to_string())
