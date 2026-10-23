# %% [markdown]
# # Ruta PAD352 (320 + 16 px de borde gris = entrada 352): umbrales en val, test una vez por semilla, exportacion, paridad, script de referencia y casos de prueba
# Regla pre-registrada (ACCEPTANCE.md, Adenda 2). Umbrales: minimo MAE en val con NMS 0,5. Modelo elegido: el de la regla original (semilla 43).

# %% [markdown]
# ## Entorno y datos
# %%
import os, io, json, shutil, subprocess, sys, time, hashlib
from pathlib import Path
import numpy as np, pandas as pd, cv2
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from IPython.display import display, Image as IPImage
subprocess.run("pip install -q ultralytics", shell=True)
from ultralytics import YOLO
from ultralytics.utils.metrics import compute_ap
OUT = Path("/kaggle/working/outputs"); OUT.mkdir(parents=True, exist_ok=True)
SRC = next(Path("/kaggle/input").glob("**/yolo_ds")); DSR = Path("/kaggle/temp/yolo_ds"); shutil.copytree(SRC, DSR)
find = lambda n: next(Path("/kaggle/input").glob(f"**/{n}"))
MODELS = {"E3": 42, "E3s43": 43, "E3s44": 44}; WTS = {m: find(f"{m}_best.pt") for m in MODELS}; CHOSEN = "E3s43"
names = "\n".join(f"  {i}: {n}" for i, n in enumerate(CLASS_NAMES))
(DSR / "data_base.yaml").write_text(f"path: {DSR}\ntrain: images/train\nval: images/val\ntest: images/test\nnames:\n{names}\n")
sp = pd.read_csv(DSR / "splits.csv", dtype={"dhash": str}); sp["stem"] = sp.name.str.replace(".jpg", "", regex=False); INFO = sp.set_index("stem"); GROUP_OF = INFO.group.to_dict(); PATTERN = INFO.pattern.to_dict()
IM = {s: sorted((DSR / "images" / s).glob("*.jpg")) for s in ("val", "test")}; GT = {s: load_gts(DSR, s) for s in ("val", "test")}
rng = np.random.default_rng(42); SUB = [IM["test"][i] for i in sorted(rng.choice(len(IM["test"]), 300, replace=False))]; G300 = {p.stem: GT["test"][p.stem] for p in SUB}
CONFS = np.round(np.arange(0.05, 0.701, 0.05), 2); IOU = 0.5

# %% [markdown]
# ## Funciones (prediccion con borde, AP50 propio, emparejamiento, IC por grupos)
# %%
def pad_canvas(bgr):
    r_ = cv2.resize(bgr, (320, 320), interpolation=cv2.INTER_LINEAR)
    return cv2.copyMakeBorder(r_, 16, 16, 16, 16, cv2.BORDER_CONSTANT, value=(114, 114, 114))

def predict_pad(model, paths, conf=0.01, iou=IOU, batch=16):
    out = {}
    for i in range(0, len(paths), batch):
        ch = paths[i:i + batch]
        for p, r in zip(ch, model.predict([pad_canvas(cv2.imread(str(p))) for p in ch], imgsz=352, conf=conf, iou=iou, verbose=False, device="cpu", max_det=1000)):
            b = r.boxes; out[Path(p).stem] = (b.cls.cpu().numpy().astype(int), b.conf.cpu().numpy(), np.clip((b.xyxy.cpu().numpy() - 16) / 320, 0, 1))
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

def match_boxes(dets, gts, conf, cls=GB, thr=0.5):
    rows = []
    for k, (gc, gbx) in gts.items():
        c, s, b = dets[k]; m = s >= conf; pc, pb = c[m], b[m]; g = np.where(gc == cls)[0]; p = np.where(pc == cls)[0]; iou = box_iou(pb[p], gbx[g]); up, ug = set(), set()
        for v, i, j in sorted(((iou[i, j], i, j) for i in range(len(p)) for j in range(len(g))), reverse=True):
            if v < thr: break
            if i in up or j in ug: continue
            up.add(i); ug.add(j)
        rows.append((k, len(up), len(p) - len(up), len(g) - len(ug), len(g), len(p)))
    return pd.DataFrame(rows, columns=["stem", "tp", "fp", "fn", "n_gt", "n_pred"]).set_index("stem")

def summ(mb):
    tp, fp, fn = mb.tp.sum(), mb.fp.sum(), mb.fn.sum(); P = tp / max(tp + fp, 1); R = tp / max(tp + fn, 1)
    return dict(n_img=len(mb), n_gt=int(mb.n_gt.sum()), precision=float(P), recall=float(R), f1=float(2 * P * R / max(P + R, 1e-9)), mae=float((mb.n_pred - mb.n_gt).abs().mean()), bias=float((mb.n_pred - mb.n_gt).mean()))

def boot_groups(mb, n=2000, seed=0):
    g = mb.index.map(GROUP_OF).to_numpy(); ug, inv = np.unique(g, return_inverse=True)
    agg = pd.DataFrame({"tp": mb.tp.to_numpy(), "fp": mb.fp.to_numpy(), "fn": mb.fn.to_numpy(), "ae": (mb.n_pred - mb.n_gt).abs().to_numpy(), "one": 1}).groupby(inv).sum().to_numpy(); rg = np.random.default_rng(seed); res = []
    for _ in range(n):
        t = agg[rg.integers(0, len(ug), len(ug))].sum(0); P = t[0] / max(t[0] + t[1], 1); R = t[0] / max(t[0] + t[2], 1); res.append((P, R, 2 * P * R / max(P + R, 1e-9), t[3] / t[4]))
    q = np.percentile(np.array(res), [2.5, 97.5], axis=0); return {k: [float(q[0, i]), float(q[1, i])] for i, k in enumerate(["precision", "recall", "f1", "mae"])}

def confusion(dets, gts, conf, thr=0.5):
    M = np.zeros((5, 5), int)
    for k, (gc, gbx) in gts.items():
        c, s, b = dets[k]; m = s >= conf; pc, pb = c[m], b[m]; iou = box_iou(gbx, pb); ug, up = set(), set()
        for v, i, j in sorted(((iou[i, j], i, j) for i in range(len(gc)) for j in range(len(pc))), reverse=True):
            if v < thr: break
            if i in ug or j in up: continue
            ug.add(i); up.add(j); M[gc[i], pc[j]] += 1
        for i in range(len(gc)):
            if i not in ug: M[gc[i], 4] += 1
        for j in range(len(pc)):
            if j not in up: M[4, pc[j]] += 1
    return pd.DataFrame(M, index=CLASS_NAMES + ["fondo"], columns=CLASS_NAMES + ["fondo"])

# %% [markdown]
# ## Paso A. Por semilla: umbrales en VAL (minimo MAE, NMS 0,5) y TEST una vez
# %%
R = {}; DT = {}
for m, sd in MODELS.items():
    mod = YOLO(str(WTS[m])); dv = predict_pad(mod, IM["val"]); fz = {}
    for nm, cl in (("estricta", [GB]), ("amplia", [GB, GPB])):
        g = pd.DataFrame([dict(conf=float(c), mae=count_metrics(dv, GT["val"], c, cl)["mae"]) for c in CONFS]); fz[nm] = dict(conf=float(g.loc[g.mae.idxmin(), "conf"]), mae_val=float(g.mae.min()), iou_nms=IOU)
    fz["val_estricta"] = summ(match_boxes(dv, GT["val"], fz["estricta"]["conf"])); fz["val_ap50"] = ap50(dv, GT["val"])
    dt = predict_pad(mod, IM["test"]); DT[m] = dt
    te = summ(match_boxes(dt, GT["test"], fz["estricta"]["conf"])); te["ap50_propio"] = ap50(dt, GT["test"]); te["mae_amplia"] = count_metrics(dt, GT["test"], fz["amplia"]["conf"], [GB, GPB])["mae"]
    R[m] = dict(seed=sd, thr=fz, test=te); print(m, json.dumps(R[m], indent=1), flush=True)
agg = {k: dict(mean=float(np.mean([R[m]["test"][k] for m in R])), sd=float(np.std([R[m]["test"][k] for m in R], ddof=1))) for k in ("precision", "recall", "f1", "mae", "ap50_propio")}
fz = R[CHOSEN]["thr"]; dets = DT[CHOSEN]; mb = match_boxes(dets, GT["test"], fz["estricta"]["conf"])
detail = dict(modelo=CHOSEN, ruta="PAD352", umbrales=fz, test=R[CHOSEN]["test"], ic95_grupos=boot_groups(mb), por_semilla_agregado=agg, todas_las_semillas=R)
for pat in ("crop", "simple"): sub = mb[mb.index.map(PATTERN) == pat]; detail[f"patron_{pat}"] = dict(**summ(sub), ic95=boot_groups(sub))
cm = confusion(dets, GT["test"], fz["estricta"]["conf"]); cm.to_csv(OUT / "confusion_test_pad352.csv"); print(cm.to_string())
tr_med = float(INFO[INFO.split == "train"][["n_c2"]].median().iloc[0]); yt = np.array([int((GT["test"][k][0] == GB).sum()) for k in GT["test"]]); detail["baseline_mediana_test_mae"] = float(np.abs(yt - tr_med).mean())
(OUT / "test_final_pad352.json").write_text(json.dumps(detail, indent=1, default=float)); print(json.dumps({k: detail[k] for k in ("ic95_grupos", "por_semilla_agregado", "patron_crop", "patron_simple")}, indent=1, default=float))

# %% [markdown]
# ## Paso B. Exportacion a 352 del modelo elegido: ONNX, TFLite FP32, FP16 (float_casting) e INT8 (calibracion 300 imagenes de train)
# %%
EX = Path("/kaggle/working/export352"); EX.mkdir(exist_ok=True); W = EX / f"ecoli_gb_{CHOSEN}_352.pt"; shutil.copy2(WTS[CHOSEN], W)
tr = INFO[INFO.split == "train"].copy(); tr["qg"] = pd.qcut(tr.gray.rank(method="first"), 4, labels=False); tr["qb"] = pd.qcut(tr.lab_b.rank(method="first"), 4, labels=False)
cal = pd.concat([g.sample(min(len(g), 19), random_state=42) for _, g in tr.groupby(["qg", "qb"])]).head(300)
(EX / "calib.txt").write_text("\n".join(str(DSR / "images" / "train" / n) for n in cal.name)); (EX / "calib.yaml").write_text(f"path: {DSR}\ntrain: {EX/'calib.txt'}\nval: {EX/'calib.txt'}\nnames:\n{names}\n")
def run_export(tag, kw, timeout=1500):
    code = f"from ultralytics import YOLO; print('EXPORT OK', YOLO('{W}').export(imgsz=352, {kw}))"; t = time.time()
    try:
        r = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, timeout=timeout, cwd=str(EX)); print(f"[{tag}] rc={r.returncode} {time.time()-t:.0f}s", (r.stdout + r.stderr)[-300:], flush=True)
    except subprocess.TimeoutExpired: print(f"[{tag}] TIMEOUT", flush=True)
run_export("onnx", "format='onnx', simplify=True"); run_export("tflite fp32", "format='tflite'")
shutil.copy2(EX / f"ecoli_gb_{CHOSEN}_352.tflite", EX / f"ecoli_gb_{CHOSEN}_352_fp32.tflite")
run_export("tflite int8", f"format='tflite', int8=True, data='{EX / 'calib.yaml'}'")
from ai_edge_quantizer import quantizer, qtyping
q = quantizer.Quantizer(str(EX / f"ecoli_gb_{CHOSEN}_352_fp32.tflite"))
q.update_quantization_recipe(regex=".*", operation_name=qtyping.TFLOperationName.ALL_SUPPORTED, algorithm_key="float_casting",
    op_config=qtyping.OpQuantizationConfig(weight_tensor_config=qtyping.TensorQuantizationConfig(num_bits=16, dtype=qtyping.TensorDataType.FLOAT), compute_precision=qtyping.ComputePrecision.FLOAT, explicit_dequantize=True))
q.quantize().export_model(str(EX / f"ecoli_gb_{CHOSEN}_352_fp16.tflite"), overwrite=True)
print(sorted(p.name for p in EX.glob("*")))

# %% [markdown]
# ## Paso C. Config de la app y script de referencia; paridad en 300 imagenes de test (pt con borde vs TFLite con el script de referencia)
# %%
APP = {"modelo": f"ecoli_gb_{CHOSEN}_352_fp16.tflite", "version": "2.0 (ruta con borde)", "clases": CLASS_NAMES, "max_det": 1000,
       "entrada": {"tamano": 352, "contenido": 320, "borde_gris_px": 16, "forma": [1, 3, 352, 352], "orden": "NCHW", "canales": "RGB", "dtype": "float32", "normalizacion": "pixel/255, sin media ni desviacion",
                   "preprocesado": "1) letterbox a 320 (lado mayor=320, relleno gris 114 centrado, bilineal); 2) agregar 16 px de gris 114 por cada lado -> 352x352"},
       "salida": {"forma": [1, 8, 2541], "filas_0_3": "cx,cy,w,h normalizados 0-1 respecto a 352", "filas_4_7": "score por clase 0-1 (sin sigmoide)", "nms_incluido": False},
       "definiciones": {"estricta": {"clases_contadas": [2], "conf": fz["estricta"]["conf"], "iou_nms": IOU}, "amplia": {"clases_contadas": [2, 3], "conf": fz["amplia"]["conf"], "iou_nms": IOU}},
       "definicion_recomendada": "estricta", "nms": "por clase; se filtra con max(score de clases) > conf; se cuenta solo si la clase ganadora esta en clases_contadas"}
(OUT / "app_config.json").write_text(json.dumps(APP, indent=1, ensure_ascii=False)); open(OUT / "inferir_tflite.py", "w", encoding="utf8").write(INFERIR_SRC)
TFF = {k: EX / f"ecoli_gb_{CHOSEN}_352{s}.tflite" for k, s in (("fp32", "_fp32"), ("fp16", "_fp16"), ("int8", "_int8"))}
i8 = sorted(EX.glob("*int8*.tflite")); TFF["int8"] = i8[0] if i8 else TFF["int8"]; print(TFF)
def script_dets(path, paths, conf_floor=0.01):
    it = cargar_interprete(str(path)); cfg = json.loads(json.dumps(APP)); cfg["definiciones"]["todas"] = {"clases_contadas": [0, 1, 2, 3], "conf": conf_floor, "iou_nms": IOU}; out = {}
    for p in paths:
        d = detectar(it, cv2.imread(str(p)), cfg, "todas")["detecciones"]
        out[p.stem] = (np.array([CLASS_NAMES.index(x["clase"]) for x in d], dtype=int), np.array([x["conf"] for x in d]), np.array([x["caja"] for x in d]).reshape(-1, 4) / 640)
    return out
par = {"pt_borde": {}, **{f"script_tflite_{k}": {} for k in TFF}}; ptm = YOLO(str(WTS[CHOSEN])); cth = fz["estricta"]["conf"]
d = predict_pad(ptm, SUB); par["pt_borde"] = dict(ap50_propio=ap50(d, G300), **summ(match_boxes(d, G300, cth)))
for k, f in TFF.items():
    if not Path(f).exists(): par[f"script_tflite_{k}"] = dict(error="no existe"); continue
    t0 = time.time(); d = script_dets(f, SUB); par[f"script_tflite_{k}"] = dict(ap50_propio=ap50(d, G300), **summ(match_boxes(d, G300, cth)), seg_300_imgs=round(time.time() - t0, 1), mb=round(Path(f).stat().st_size / 1e6, 2))
(OUT / "paridad_300_pad352.json").write_text(json.dumps(par, indent=1)); print(json.dumps(par, indent=1))
import tensorflow as tf
io_ = {}
for k, f in TFF.items():
    if Path(f).exists():
        it_ = tf.lite.Interpreter(model_path=str(f)); it_.allocate_tensors(); io_[k] = dict(inputs=[dict(shape=x["shape"].tolist(), dtype=x["dtype"].__name__, quant=[float(x["quantization"][0]), int(x["quantization"][1])]) for x in it_.get_input_details()],
                                                                                       outputs=[dict(shape=x["shape"].tolist(), dtype=x["dtype"].__name__, quant=[float(x["quantization"][0]), int(x["quantization"][1])]) for x in it_.get_output_details()])
(OUT / "tflite_io_352.json").write_text(json.dumps(io_, indent=1)); print(json.dumps(io_)[:900])

# %% [markdown]
# ## Paso D. Verificacion del script contra ultralytics (misma entrada con borde) y 20 casos de prueba con etiqueta y prediccion impresas
# %%
it = cargar_interprete(str(TFF["fp16"])); uc = YOLO(str(TFF["fp16"]), task="detect"); agree, diffs = 0, []
for p in SUB:
    img = cv2.imread(str(p)); mine = detectar(it, img, APP, "estricta"); r = uc.predict(pad_canvas(img), imgsz=352, conf=cth, iou=IOU, device="cpu", verbose=False, max_det=1000)[0]
    sel = r.boxes.cls.cpu().numpy() == GB; bu = np.clip((r.boxes.xyxy.cpu().numpy()[sel] - 16) / 320 * 640, 0, 640); bm = np.array([x["caja"] for x in mine["detecciones"] if x["clase"] == "G-bacilli"]).reshape(-1, 4)
    agree += int(int(sel.sum()) == mine["conteo"])
    if len(bu) == len(bm) and len(bu): diffs.append(float(np.abs(np.sort(bu, 0) - np.sort(bm, 0)).max()))
ver = dict(imagenes=len(SUB), conteo_igual_a_ultralytics=agree, max_dif_caja_px=float(max(diffs)) if diffs else None, mediana_dif_caja_px=float(np.median(diffs)) if diffs else None); print("script vs ultralytics:", ver)
CAS = OUT / "casos"; CAS.mkdir(exist_ok=True); rs = np.random.default_rng(7); stems = [p.stem for p in IM["test"]]; ngt = {s: int((GT["test"][s][0] == GB).sum()) for s in stems}; pick = []
for lo, hi, n in ((0, 0, 4), (1, 1, 6), (2, 3, 5), (4, 99, 5)): pick += list(rs.choice([s for s in stems if lo <= ngt[s] <= hi], n, replace=False))
def panel(img_bgr, gc, gb, dets):
    gt_v = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB).copy(); pr_v = gt_v.copy()
    for c, b in zip(gc, gb):
        x1, y1, x2, y2 = (b * 640).astype(int); cv2.rectangle(gt_v, (x1, y1), (x2, y2), (0, 200, 0) if c == GB else (255, 160, 0), 2)
    for d in dets:
        x1, y1, x2, y2 = [int(v) for v in d["caja"]]; col = (255, 0, 0) if d["clase"] == "G-bacilli" else (150, 0, 255); cv2.rectangle(pr_v, (x1, y1), (x2, y2), col, 2); cv2.putText(pr_v, f'{d["conf"]:.2f}', (x1, max(y1 - 6, 14)), 0, 0.6, col, 2)
    return gt_v, pr_v
print("Leyenda: verde = G-bacilli real (etiqueta); naranja = otra clase real; rojo = prediccion G-bacilli (con confianza); violeta = prediccion de otra clase")
casos = []
for n_, s in enumerate(pick, 1):
    img = cv2.imread(str(DSR / "images" / "test" / f"{s}.jpg")); cv2.imwrite(str(CAS / f"{s}.jpg"), img); res_ = detectar(it, img, APP, "estricta"); res_a = detectar(it, img, APP, "amplia"); gc, gb = GT["test"][s]
    gtb = [[round(float(v), 1) for v in (b * 640)] for c, b in zip(gc, gb) if c == GB]; dg = [d for d in res_["detecciones"] if d["clase"] == "G-bacilli"]
    iou_ = box_iou(np.array([d["caja"] for d in dg]).reshape(-1, 4) / 640, np.array(gtb).reshape(-1, 4) / 640); tp_ = len(set(np.where(iou_ >= 0.5)[0])) if iou_.size else 0
    casos.append(dict(imagen=f"{s}.jpg", patron=INFO.pattern[s], gt_G_bacilli=len(gtb), gt_cajas_G_bacilli=gtb, esperado_script_estricta=res_["conteo"], esperado_script_amplia=res_a["conteo"], detecciones_estricta=dg))
    print(f"[{n_:02d}/20] {s}.jpg ({INFO.pattern[s]}) | etiqueta real G-bacilli = {len(gtb)} | prediccion del modelo = {res_['conteo']} (confianzas {[d['conf'] for d in dg]}) | aciertos~{tp_} falsos+~{len(dg) - tp_} perdidos~{len(gtb) - tp_}")
    gt_v, pr_v = panel(img, gc, gb, res_["detecciones"]); fig, ax = plt.subplots(1, 2, figsize=(12, 6.2)); ax[0].imshow(gt_v); ax[0].set_title(f"ETIQUETA REAL: {len(gtb)} G-bacilli", fontsize=13); ax[1].imshow(pr_v); ax[1].set_title(f"PREDICCION DEL MODELO: {res_['conteo']} G-bacilli", fontsize=13)
    for x in ax: x.axis("off")
    plt.tight_layout(); buf = io.BytesIO(); fig.savefig(buf, format="png", dpi=70); plt.close(fig); display(IPImage(data=buf.getvalue())); (CAS / f"panel_{n_:02d}_{s}.png").write_bytes(buf.getvalue())
nosq = []
for k, s in enumerate(pick[4:7]):
    full = cv2.imread(str(DSR / "images" / "test" / f"{s}.jpg")); y0, y1, x0, x1 = ((80, 560, 0, 640), (0, 640, 100, 500), (160, 400, 20, 600))[k]; crop = full[y0:y1, x0:x1]
    cv2.imwrite(str(CAS / f"nocuadrada_{k}_{s}.jpg"), crop); mc = detectar(it, crop, APP, "estricta"); mf = detectar(it, full, APP, "estricta")
    inside = [d for d in mf["detecciones"] if d["clase"] == "G-bacilli" and d["caja"][0] > x0 + 15 and d["caja"][2] < x1 - 15 and d["caja"][1] > y0 + 15 and d["caja"][3] < y1 - 15]
    bc = np.array([[d["caja"][0] + x0, d["caja"][1] + y0, d["caja"][2] + x0, d["caja"][3] + y0] for d in mc["detecciones"] if d["clase"] == "G-bacilli"]).reshape(-1, 4); bf = np.array([d["caja"] for d in inside]).reshape(-1, 4)
    iou_ = box_iou(bf, bc) if len(bf) and len(bc) else np.zeros((0, 0))
    nosq.append(dict(imagen=f"nocuadrada_{k}_{s}.jpg", forma=list(crop.shape[:2]), conteo_recorte=mc["conteo"], cajas_en_imagen_completa_dentro_del_recorte=int(len(bf)), cajas_del_recorte_con_IoU_mayor_0_7_con_la_completa=int((iou_.max(1) > 0.7).sum()) if iou_.size else 0, detecciones_recorte=mc["detecciones"]))
(OUT / "casos_prueba.json").write_text(json.dumps(dict(config="app_config.json", modelo=APP["modelo"], verificacion_vs_ultralytics_300=ver, casos=casos, no_cuadradas=nosq), indent=1, ensure_ascii=False))
tab = pd.DataFrame([(c["imagen"], c["patron"], c["gt_G_bacilli"], c["esperado_script_estricta"], c["esperado_script_amplia"]) for c in casos], columns=["imagen", "patron", "GT", "modelo_estricta", "modelo_amplia"]); tab["error"] = tab.modelo_estricta - tab.GT
tab.to_csv(OUT / "casos_prueba_tabla.csv", index=False); print(tab.to_string()); print("MAE en estos 20:", tab.error.abs().mean()); print("no cuadradas:", json.dumps([{k: v for k, v in n.items() if k != "detecciones_recorte"} for n in nosq]))
print("script vs ultralytics:", ver)
for f in EX.glob("ecoli_gb_*"):
    if f.suffix in (".pt", ".onnx", ".tflite"): shutil.copy2(f, OUT / f.name)
(OUT / "sha256_pesos_352.json").write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.glob("ecoli_gb_*") if p.suffix in (".pt", ".onnx", ".tflite")}, indent=1))
(OUT / "pip_freeze_final.txt").write_text(subprocess.run("pip freeze | grep -i -E '^(ultralytics|torch|tensorflow|onnx|onnx2tf|onnxruntime|numpy|ai-edge|opencv|scikit|scipy|pandas)'", shell=True, capture_output=True, text=True).stdout)
