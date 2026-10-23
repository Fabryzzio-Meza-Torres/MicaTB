# %% [markdown]
# # Verificaciones del modelo final (CPU): paridad, vecinos de id, respaldo 416, script de referencia y casos de prueba
# Modelo: E3s43 (320 px). Umbrales congelados en val: estricta conf 0,60 / NMS 0,5; amplia conf 0,45 / NMS 0,5; max_det 1000.
# No se usa test para elegir nada: solo para comprobar paridad, fuga y comportamiento.

# %% [markdown]
# ## Entorno y datos
# %%
import os, json, shutil, subprocess, sys, time, hashlib
from pathlib import Path
import numpy as np, pandas as pd, cv2
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
subprocess.run("pip install -q ultralytics", shell=True)
from ultralytics import YOLO
from ultralytics.utils.metrics import compute_ap
OUT = Path("/kaggle/working/outputs"); OUT.mkdir(parents=True, exist_ok=True)
SRC = next(Path("/kaggle/input").glob("**/yolo_ds")); DSR = Path("/kaggle/temp/yolo_ds"); shutil.copytree(SRC, DSR)
find = lambda n: next(Path("/kaggle/input").glob(f"**/{n}"))
W3, W2 = find("E3s43_best.pt"), find("E2_best.pt")
TF = {k: find(f"ecoli_gb_E3s43{s}.tflite") for k, s in (("fp32", "_fp32"), ("fp16", "_fp16"), ("int8", "_int8"))}
names = "\n".join(f"  {i}: {n}" for i, n in enumerate(CLASS_NAMES))
(DSR / "data_base.yaml").write_text(f"path: {DSR}\ntrain: images/train\nval: images/val\ntest: images/test\nnames:\n{names}\n")
sp = pd.read_csv(DSR / "splits.csv", dtype={"dhash": str}); sp["stem"] = sp.name.str.replace(".jpg", "", regex=False); INFO = sp.set_index("stem")
GROUP_OF = INFO.group.to_dict()
IM = {s: sorted((DSR / "images" / s).glob("*.jpg")) for s in ("val", "test")}; GT = {s: load_gts(DSR, s) for s in ("val", "test")}
rng = np.random.default_rng(42); SUB = [IM["test"][i] for i in sorted(rng.choice(len(IM["test"]), 300, replace=False))]
G300 = {p.stem: GT["test"][p.stem] for p in SUB}
(DSR / "par300.txt").write_text("\n".join(map(str, SUB))); (DSR / "par300.yaml").write_text(f"path: {DSR}\ntrain: {DSR/'par300.txt'}\nval: {DSR/'par300.txt'}\ntest: {DSR/'par300.txt'}\nnames:\n{names}\n")
CONF, IOU = 0.6, 0.5
print("modelos:", W3, W2, TF)

# %% [markdown]
# ## Funciones: AP50 propio sobre el camino de inferencia (predict) y metricas por imagen
# %%
def ap50(dets, gts, cls=GB):
    rows, ngt = [], 0
    for k, (gc, gb) in gts.items():
        c, s, b = dets[k]; p = np.where(c == cls)[0]; g = np.where(gc == cls)[0]; ngt += len(g)
        order = p[np.argsort(-s[p])]; iou = box_iou(b[order], gb[g]); used = set()
        for i, pi in enumerate(order):
            best, bv = -1, 0.5
            for j in range(len(g)):
                if j not in used and iou[i, j] >= bv: best, bv = j, iou[i, j]
            if best >= 0: used.add(best)
            rows.append((s[pi], int(best >= 0)))
    rows.sort(key=lambda r: -r[0]); tp = np.cumsum([r[1] for r in rows]); fp = np.cumsum([1 - r[1] for r in rows])
    return float(compute_ap(tp / max(ngt, 1), tp / np.maximum(tp + fp, 1))[0])

def match_boxes(dets, gts, conf, cls=GB, thr=0.5):
    rows = []
    for k, (gc, gbx) in gts.items():
        c, s, b = dets[k]; m = s >= conf; pc, pb = c[m], b[m]
        g = np.where(gc == cls)[0]; p = np.where(pc == cls)[0]; iou = box_iou(pb[p], gbx[g]); up, ug = set(), set()
        for v, i, j in sorted(((iou[i, j], i, j) for i in range(len(p)) for j in range(len(g))), reverse=True):
            if v < thr: break
            if i in up or j in ug: continue
            up.add(i); ug.add(j)
        rows.append((k, len(up), len(p) - len(up), len(g) - len(ug), len(g), len(p)))
    return pd.DataFrame(rows, columns=["stem", "tp", "fp", "fn", "n_gt", "n_pred"]).set_index("stem")

def summ(mb):
    tp, fp, fn = mb.tp.sum(), mb.fp.sum(), mb.fn.sum(); P = tp / max(tp + fp, 1); R = tp / max(tp + fn, 1)
    return dict(n_img=len(mb), n_gt=int(mb.n_gt.sum()), precision=float(P), recall=float(R), f1=float(2 * P * R / max(P + R, 1e-9)), mae=float((mb.n_pred - mb.n_gt).abs().mean()))

# %% [markdown]
# ## Bloque 1a. Paridad: ¿la diferencia 0,955 (test completo) vs 0,906 (300 imagenes, exportados) es muestreo, modo de validacion o conversion?
# Se mide el mismo .pt en las mismas 300 imagenes con distintos modos de `val`, y con un AP50 propio sobre el camino de inferencia (predict), igual para pt y los tres .tflite.
# %%
m3 = YOLO(str(W3)); par = {"val_ultralytics_pt_300": {}, "val_ultralytics_pt_test_completo": {}, "ap50_propio_predict_300": {}, "operacion_conf06_300": {}}
for rect in (True, False):
    for bs in (16, 1):
        v = m3.val(data=str(DSR / "par300.yaml"), split="val", imgsz=320, conf=0.001, iou=0.7, max_det=1000, device="cpu", batch=bs, rect=rect, verbose=False, plots=False)
        par["val_ultralytics_pt_300"][f"rect={rect},batch={bs}"] = float(v.box.ap50[list(v.box.ap_class_index).index(GB)])
for rect in (True, False):
    v = m3.val(data=str(DSR / "data_base.yaml"), split="test", imgsz=320, conf=0.001, iou=0.7, max_det=1000, device="cpu", batch=16, rect=rect, verbose=False, plots=False)
    par["val_ultralytics_pt_test_completo"][f"rect={rect}"] = float(v.box.ap50[list(v.box.ap_class_index).index(GB)])
models = {"pt": m3, **{f"tflite_{k}": YOLO(str(p), task="detect") for k, p in TF.items()}}
DET300 = {}
for k, mod in models.items():
    DET300[k] = predict_dets(mod, SUB, 320, conf=0.01, iou=IOU, device="cpu", batch=1)
    par["ap50_propio_predict_300"][k] = ap50(DET300[k], G300); par["operacion_conf06_300"][k] = summ(match_boxes(DET300[k], G300, CONF))
DET_T = predict_dets(m3, IM["test"], 320, conf=0.01, iou=IOU, device="cpu", batch=1); par["ap50_propio_predict_test_completo_pt"] = ap50(DET_T, GT["test"])
(OUT / "paridad_diagnostico.json").write_text(json.dumps(par, indent=1)); print(json.dumps(par, indent=1))

# %% [markdown]
# ## Bloque 1b. Fuga por ids vecinos: rendimiento en test segun si hay un vecino de id (distancia 1 o 2) en train
# %%
tr_ids = set(INFO[INFO.split == "train"].group_id.astype(int)); mb_t = match_boxes(DET_T, GT["test"], CONF)
ids = INFO.loc[mb_t.index].group_id.astype(int)
near = lambda i, d: any((i + o) in tr_ids for o in range(-d, d + 1) if o != 0)
mb_t["vec1"] = [near(i, 1) for i in ids]; mb_t["vec2"] = [near(i, 2) for i in ids]; mb_t["grupo"] = mb_t.index.map(GROUP_OF)
res = {}
def boot_diff(flag, n=1000, seed=0):
    a = mb_t.assign(f=mb_t[flag]).groupby(["grupo", "f"])[["tp", "fp", "fn", "n_gt", "n_pred"]].sum().reset_index(); gs = a.grupo.unique(); rng = np.random.default_rng(seed); d = []
    by = {g: a[a.grupo == g] for g in gs}
    for _ in range(n):
        s = pd.concat([by[g] for g in rng.choice(gs, len(gs))]); r = {}
        for f in (True, False):
            t = s[s.f == f][["tp", "fp", "fn"]].sum(); P = t.tp / max(t.tp + t.fp, 1); R = t.tp / max(t.tp + t.fn, 1); r[f] = (R, 2 * P * R / max(P + R, 1e-9))
        d.append((r[True][0] - r[False][0], r[True][1] - r[False][1]))
    q = np.percentile(np.array(d), [2.5, 97.5], axis=0); return dict(recall_dif_ic95=[float(q[0, 0]), float(q[1, 0])], f1_dif_ic95=[float(q[0, 1]), float(q[1, 1])])
for flag in ("vec1", "vec2"):
    res[flag] = {"con_vecino": summ(mb_t[mb_t[flag]]), "sin_vecino": summ(mb_t[~mb_t[flag]]), **boot_diff(flag)}
res["por_patron"] = {p: {"n": int((INFO.loc[mb_t.index].pattern == p).sum()), "con_vecino_dist2": int(mb_t[INFO.loc[mb_t.index].pattern == p].vec2.sum())} for p in ("crop", "simple")}
(OUT / "vecinos_id_test.json").write_text(json.dumps(res, indent=1)); print(json.dumps(res, indent=1))

# %% [markdown]
# ## Bloque 1c. Respaldo 416 px (E2): umbrales en val, exportacion ONNX/FP32/FP16 y paridad en las 300 imagenes
# %%
m2 = YOLO(str(W2)); dv = {i: predict_dets(m2, IM["val"], 416, conf=0.01, iou=i, device="cpu") for i in (0.5, 0.6, 0.7)}
g = pd.DataFrame([dict(iou=i, conf=float(c), mae=count_metrics(dv[i], GT["val"], c, [GB])["mae"]) for i in dv for c in np.round(np.arange(0.05, 0.701, 0.05), 2)])
bst = g.sort_values(["mae", "iou", "conf"]).iloc[0]; thr416 = dict(conf=float(bst.conf), iou_nms=float(bst.iou), mae_val=float(bst.mae))
EX = Path("/kaggle/working/export416"); EX.mkdir(exist_ok=True); w = EX / "ecoli_gb_E2_416.pt"; shutil.copy2(W2, w)
def exp(tag, kw):
    r = subprocess.run([sys.executable, "-c", f"from ultralytics import YOLO; print(YOLO('{w}').export(imgsz=416, {kw}))"], capture_output=True, text=True, timeout=1500, cwd=str(EX)); print(tag, r.returncode, (r.stdout + r.stderr)[-200:])
exp("onnx", "format='onnx', simplify=True"); exp("tflite fp32", "format='tflite'")
shutil.copy2(EX / "ecoli_gb_E2_416.tflite", EX / "ecoli_gb_E2_416_fp32.tflite")
from ai_edge_quantizer import quantizer, qtyping
q = quantizer.Quantizer(str(EX / "ecoli_gb_E2_416_fp32.tflite"))
q.update_quantization_recipe(regex=".*", operation_name=qtyping.TFLOperationName.ALL_SUPPORTED, algorithm_key="float_casting",
    op_config=qtyping.OpQuantizationConfig(weight_tensor_config=qtyping.TensorQuantizationConfig(num_bits=16, dtype=qtyping.TensorDataType.FLOAT), compute_precision=qtyping.ComputePrecision.FLOAT, explicit_dequantize=True))
q.quantize().export_model(str(EX / "ecoli_gb_E2_416_fp16.tflite"), overwrite=True)
r416 = {"umbrales_val": thr416, "paridad_300": {}}
for k, p in (("pt", w), ("tflite_fp16", EX / "ecoli_gb_E2_416_fp16.tflite")):
    d = predict_dets(YOLO(str(p), task="detect"), SUB, 416, conf=0.01, iou=thr416["iou_nms"], device="cpu", batch=1)
    r416["paridad_300"][k] = dict(ap50_propio=ap50(d, G300), **summ(match_boxes(d, G300, thr416["conf"])))
(OUT / "respaldo416.json").write_text(json.dumps(r416, indent=1)); print(json.dumps(r416, indent=1))
for f in EX.glob("ecoli_gb_E2_416*"):
    if f.suffix in (".pt", ".onnx", ".tflite"): shutil.copy2(f, OUT / f.name)

# %% [markdown]
# ## Bloque 2. Config de la app, script de referencia y verificacion contra ultralytics (300 imagenes de test)
# %%
APP = {"modelo": "ecoli_gb_E3s43_fp16.tflite", "version": "1.0", "clases": CLASS_NAMES, "max_det": 1000,
       "entrada": {"tamano": 320, "forma": [1, 3, 320, 320], "orden": "NCHW", "canales": "RGB", "dtype": "float32", "normalizacion": "pixel/255, sin media ni desviacion", "letterbox": "lado mayor=320, relleno gris 114 centrado, interpolacion bilineal"},
       "salida": {"forma": [1, 8, 2100], "filas_0_3": "cx,cy,w,h normalizados 0-1 respecto a 320", "filas_4_7": "score por clase 0-1 (sin sigmoide)", "nms_incluido": False},
       "definiciones": {"estricta": {"clases_contadas": [2], "conf": 0.60, "iou_nms": 0.5}, "amplia": {"clases_contadas": [2, 3], "conf": 0.45, "iou_nms": 0.5}},
       "definicion_recomendada": "estricta", "nms": "por clase; se filtra con max(score de clases) > conf; se cuenta solo si la clase ganadora esta en clases_contadas"}
(OUT / "app_config.json").write_text(json.dumps(APP, indent=1, ensure_ascii=False))
it = cargar_interprete(str(TF["fp16"])); R = []
for p in SUB:
    img = cv2.imread(str(p))
    mine = detectar(it, img, APP, "estricta"); R.append((p.stem, mine))
uc = YOLO(str(TF["fp16"]), task="detect"); agree, maxdiff, diffs = 0, 0.0, []
for (st, mine), p in zip(R, SUB):
    r = uc.predict(str(p), imgsz=320, conf=CONF, iou=IOU, device="cpu", verbose=False, max_det=1000)[0]
    n_u = int((r.boxes.cls.cpu().numpy() == GB).sum()); agree += int(n_u == mine["conteo"])
    bu = r.boxes.xyxy.cpu().numpy()[r.boxes.cls.cpu().numpy() == GB]; bm = np.array([d["caja"] for d in mine["detecciones"] if d["clase"] == "G-bacilli"]).reshape(-1, 4)
    if len(bu) == len(bm) and len(bu): diffs.append(float(np.abs(np.sort(bu, 0) - np.sort(bm, 0)).max()))
ver = dict(imagenes=len(R), conteo_igual_a_ultralytics=agree, max_dif_caja_px=float(max(diffs)) if diffs else None, mediana_dif_caja_px=float(np.median(diffs)) if diffs else None)
print("script vs ultralytics:", ver)

# %% [markdown]
# ## Casos de prueba (20 imagenes de test con resultado esperado) y 3 recortes no cuadrados (prueba del letterbox)
# %%
CAS = OUT / "casos"; CAS.mkdir(exist_ok=True); rs = np.random.default_rng(7); stems = [p.stem for p in IM["test"]]; ngt = {s: int((GT["test"][s][0] == GB).sum()) for s in stems}
pick = []
for lo, hi, n in ((0, 0, 4), (1, 1, 6), (2, 3, 5), (4, 99, 5)):
    pool = [s for s in stems if lo <= ngt[s] <= hi]; pick += list(rs.choice(pool, n, replace=False))
casos = []
import io
from IPython.display import display, Image as IPImage
def panel(img_bgr, gc, gb, dets):
    gt_v = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB).copy(); pr_v = gt_v.copy()
    for c, b in zip(gc, gb):
        x1, y1, x2, y2 = (b * 640).astype(int); cv2.rectangle(gt_v, (x1, y1), (x2, y2), (0, 200, 0) if c == GB else (255, 160, 0), 2)
    for d in dets:
        x1, y1, x2, y2 = [int(v) for v in d["caja"]]; col = (255, 0, 0) if d["clase"] == "G-bacilli" else (150, 0, 255)
        cv2.rectangle(pr_v, (x1, y1), (x2, y2), col, 2); cv2.putText(pr_v, f'{d["conf"]:.2f}', (x1, max(y1 - 6, 14)), 0, 0.6, col, 2)
    return gt_v, pr_v
print("Leyenda: verde = G-bacilli real (etiqueta); naranja = otra clase real; rojo = prediccion G-bacilli (con confianza); violeta = prediccion de otra clase")
for n_, s in enumerate(pick, 1):
    img = cv2.imread(str(DSR / "images" / "test" / f"{s}.jpg")); cv2.imwrite(str(CAS / f"{s}.jpg"), img)
    res_ = detectar(it, img, APP, "estricta"); res_a = detectar(it, img, APP, "amplia"); gc, gb = GT["test"][s]
    gtb = [[round(float(v), 1) for v in (b * 640)] for c, b in zip(gc, gb) if c == GB]
    dg = [d for d in res_["detecciones"] if d["clase"] == "G-bacilli"]
    iou_ = box_iou(np.array([d["caja"] for d in dg]).reshape(-1, 4) / 640, np.array(gtb).reshape(-1, 4) / 640); tp_ = len({j for i, j in zip(*np.where(iou_ >= 0.5))}) if iou_.size else 0
    casos.append(dict(imagen=f"{s}.jpg", gt_G_bacilli=len(gtb), gt_cajas_G_bacilli=gtb, esperado_script_estricta=res_["conteo"], esperado_script_amplia=res_a["conteo"], detecciones_estricta=dg, patron=INFO.pattern[s]))
    print(f"[{n_:02d}/20] {s}.jpg ({INFO.pattern[s]}) | etiqueta real G-bacilli = {len(gtb)} | prediccion del modelo = {res_['conteo']} (confianzas {[d['conf'] for d in dg]}) | aciertos~{tp_} falsos+~{len(dg) - tp_} perdidos~{len(gtb) - tp_}")
    gt_v, pr_v = panel(img, gc, gb, res_["detecciones"])
    fig, ax = plt.subplots(1, 2, figsize=(12, 6.2)); ax[0].imshow(gt_v); ax[0].set_title(f"ETIQUETA REAL: {len(gtb)} G-bacilli", fontsize=13); ax[1].imshow(pr_v); ax[1].set_title(f"PREDICCION DEL MODELO: {res_['conteo']} G-bacilli", fontsize=13)
    for x in ax: x.axis("off")
    plt.tight_layout(); buf = io.BytesIO(); fig.savefig(buf, format="png", dpi=70); plt.close(fig); display(IPImage(data=buf.getvalue()))
    if n_ <= 20: open(CAS / f"panel_{n_:02d}_{s}.png", "wb").write(buf.getvalue())
nosq = []
for k, s in enumerate(pick[4:7]):
    img = cv2.imread(str(DSR / "images" / "test" / f"{s}.jpg"))[:, :, :]; crop = img[80:560, 0:640] if k == 0 else (img[0:640, 100:500] if k == 1 else img[160:400, 20:620])
    cv2.imwrite(str(CAS / f"nocuadrada_{k}_{s}.jpg"), crop); mine = detectar(it, crop, APP, "estricta")
    r = uc.predict(str(CAS / f"nocuadrada_{k}_{s}.jpg"), imgsz=320, conf=CONF, iou=IOU, device="cpu", verbose=False, max_det=1000)[0]
    bu = r.boxes.xyxy.cpu().numpy()[r.boxes.cls.cpu().numpy() == GB]; bm = np.array([d["caja"] for d in mine["detecciones"] if d["clase"] == "G-bacilli"]).reshape(-1, 4)
    nosq.append(dict(imagen=f"nocuadrada_{k}_{s}.jpg", forma=list(crop.shape[:2]), conteo_script=mine["conteo"], conteo_ultralytics=int(len(bu)),
                     max_dif_caja_px=float(np.abs(np.sort(bu, 0) - np.sort(bm, 0)).max()) if len(bu) == len(bm) and len(bu) else None, detecciones=mine["detecciones"]))
(OUT / "casos_prueba.json").write_text(json.dumps(dict(config="app_config.json", modelo="ecoli_gb_E3s43_fp16.tflite", verificacion_vs_ultralytics_300=ver, casos=casos, no_cuadradas=nosq), indent=1, ensure_ascii=False))
tab = pd.DataFrame([(c["imagen"], c["patron"], c["gt_G_bacilli"], c["esperado_script_estricta"], c["esperado_script_amplia"]) for c in casos], columns=["imagen", "patron", "GT", "modelo_estricta", "modelo_amplia"])
tab["error"] = tab.modelo_estricta - tab.GT; print(tab.to_string()); print("MAE en estos 20:", tab.error.abs().mean(), "| no cuadradas:", json.dumps([{k: v for k, v in n.items() if k != "detecciones"} for n in nosq]))
tab.to_csv(OUT / "casos_prueba_tabla.csv", index=False)
open(OUT / "inferir_tflite.py", "w", encoding="utf8").write(INFERIR_SRC)
