# %% [markdown]
# # Paso 8 (CPU): exportacion del modelo elegido y paridad en 300 imagenes de test
# Regla pre-registrada en ACCEPTANCE.md: elegido = mayor F1 G-bacilli en val al conf de minimo MAE de conteo.

# %% [markdown]
# ## Entorno, datos y pesos
# %%
import os, json, pickle, shutil, subprocess, sys, time, hashlib
from pathlib import Path
import numpy as np, pandas as pd, cv2
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
subprocess.run("pip install -q ultralytics", shell=True)
from ultralytics import YOLO
OUT = Path("/kaggle/working/outputs"); OUT.mkdir(parents=True, exist_ok=True)
SRC = next(Path("/kaggle/input").glob("**/yolo_ds")); DSR = Path("/kaggle/temp/yolo_ds"); shutil.copytree(SRC, DSR)
MODELS = {"E3s43": 43}
WTS = {m: next(Path("/kaggle/input").glob(f"**/{m}_best.pt")) for m in MODELS}; print(WTS)
names = "\n".join(f"  {i}: {n}" for i, n in enumerate(CLASS_NAMES))
sp = pd.read_csv(DSR / "splits.csv", dtype={"dhash": str}); sp["stem"] = sp.name.str.replace(".jpg", "", regex=False); INFO = sp.set_index("stem")
GROUP_OF = INFO.group.to_dict(); PATTERN = INFO.pattern.to_dict()
IM = {s: sorted((DSR / "images" / s).glob("*.jpg")) for s in ("val", "test")}; GT = {s: load_gts(DSR, s) for s in ("val", "test")}
(DSR / "data_base.yaml").write_text(f"path: {DSR}\ntrain: images/train\nval: images/val\ntest: images/test\nnames:\n{names}\n")
CONFS = np.round(np.arange(0.05, 0.701, 0.05), 2)

def match_boxes(dets, gts, conf, cls=GB, thr=0.5):
    """Por imagen: tp, fp, fn de la clase cls (IoU>=thr, 1 a 1 greedy)."""
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

def summarize(mb):
    tp, fp, fn = mb.tp.sum(), mb.fp.sum(), mb.fn.sum(); P = tp / max(tp + fp, 1); R = tp / max(tp + fn, 1)
    return dict(precision=float(P), recall=float(R), f1=float(2 * P * R / max(P + R, 1e-9)), mae=float((mb.n_pred - mb.n_gt).abs().mean()), bias=float((mb.n_pred - mb.n_gt).mean()), n_img=len(mb), n_gt=int(mb.n_gt.sum()))

def boot_groups(mb, n=2000, seed=0):
    """IC 95 % remuestreando grupos de origen: precision, recall, F1, MAE."""
    g = mb.index.map(GROUP_OF).to_numpy(); ug, inv = np.unique(g, return_inverse=True)
    agg = pd.DataFrame({"tp": mb.tp.to_numpy(), "fp": mb.fp.to_numpy(), "fn": mb.fn.to_numpy(), "ae": (mb.n_pred - mb.n_gt).abs().to_numpy(), "one": 1}).groupby(inv).sum().to_numpy()
    rng = np.random.default_rng(seed); res = []
    for _ in range(n):
        t = agg[rng.integers(0, len(ug), len(ug))].sum(0); P = t[0] / max(t[0] + t[1], 1); R = t[0] / max(t[0] + t[2], 1); res.append((P, R, 2 * P * R / max(P + R, 1e-9), t[3] / t[4]))
    q = np.percentile(np.array(res), [2.5, 97.5], axis=0)
    return {k: [float(q[0, i]), float(q[1, i])] for i, k in enumerate(["precision", "recall", "f1", "mae"])}

# %% [markdown]
# ## Modelo y umbrales congelados (elegidos por VAL en el kernel ecoli-final; aqui no se re-evalua el test completo)
# %%
CHOSEN = "E3s43"; VAL = {CHOSEN: {"estricta": {"iou": 0.5, "conf": 0.6}, "amplia": {"iou": 0.5, "conf": 0.45}}}

# %% [markdown]
# ## Paso 8a. Exportacion del elegido: ONNX, TFLite FP32, FP16 (float_casting) e INT8 (calibracion 300 imagenes de train)
# %%
EX = Path("/kaggle/working/export"); EX.mkdir(exist_ok=True); W = EX / f"ecoli_gb_{CHOSEN}.pt"; shutil.copy2(WTS[CHOSEN], W)
tr = INFO[INFO.split == "train"].copy(); rng = np.random.default_rng(42)
tr["qg"] = pd.qcut(tr.gray.rank(method="first"), 4, labels=False); tr["qb"] = pd.qcut(tr.lab_b.rank(method="first"), 4, labels=False)
cal = pd.concat([g.sample(min(len(g), 19), random_state=42) for _, g in tr.groupby(["qg", "qb"])]).head(300)
if len(cal) < 300: cal = pd.concat([cal, tr.drop(cal.index).sample(300 - len(cal), random_state=42)])
(EX / "calib.txt").write_text("\n".join(str(DSR / "images" / "train" / n) for n in cal.name)); print("calibracion:", len(cal), "imagenes", cal.groupby(["qg", "qb"]).size().describe()[["min", "max"]].to_dict())
(EX / "calib.yaml").write_text(f"path: {DSR}\ntrain: {EX / 'calib.txt'}\nval: {EX / 'calib.txt'}\nnames:\n{names}\n")
def run_export(tag, kw, timeout=1500):
    code = f"from ultralytics import YOLO; print('EXPORT OK', YOLO('{W}').export(imgsz=320, {kw}))"; t = time.time()
    try:
        r = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, timeout=timeout, cwd=str(EX)); print(f"[{tag}] rc={r.returncode} {time.time()-t:.0f}s", (r.stdout + r.stderr)[-500:], flush=True); return r.returncode == 0
    except subprocess.TimeoutExpired: print(f"[{tag}] TIMEOUT", flush=True); return False
ok = {"onnx": run_export("onnx", "format='onnx', simplify=True"), "fp32": run_export("tflite fp32", "format='tflite'"), "int8": run_export("tflite int8", f"format='tflite', int8=True, data='{EX / 'calib.yaml'}'")}
print(sorted(p.name for p in EX.glob("*")))
fp32 = EX / f"ecoli_gb_{CHOSEN}.tflite"
if fp32.exists(): shutil.copy2(fp32, EX / f"ecoli_gb_{CHOSEN}_fp32.tflite")
try:
    from ai_edge_quantizer import quantizer, qtyping
    q = quantizer.Quantizer(str(EX / f"ecoli_gb_{CHOSEN}_fp32.tflite"))
    cfg = qtyping.OpQuantizationConfig(weight_tensor_config=qtyping.TensorQuantizationConfig(num_bits=16, dtype=qtyping.TensorDataType.FLOAT), compute_precision=qtyping.ComputePrecision.FLOAT, explicit_dequantize=True)
    q.update_quantization_recipe(regex=".*", operation_name=qtyping.TFLOperationName.ALL_SUPPORTED, algorithm_key="float_casting", op_config=cfg)
    q.quantize().export_model(str(EX / f"ecoli_gb_{CHOSEN}_fp16.tflite"), overwrite=True); ok["fp16"] = True
except Exception as e: print("FP16 fallo", repr(e)[:400]); ok["fp16"] = False
print(ok)

# %% [markdown]
# ## Paso 8b. Paridad en 300 imagenes de test (pt, ONNX, FP32, FP16, INT8): mAP50 G-bacilli y conteo; I/O de los TFLite; latencia proxy
# %%
rng = np.random.default_rng(42); sub = [IM["test"][i] for i in sorted(rng.choice(len(IM["test"]), 300, replace=False))]
(EX / "par300.txt").write_text("\n".join(map(str, sub))); (EX / "par300.yaml").write_text(f"path: {DSR}\ntrain: {EX / 'par300.txt'}\nval: {EX / 'par300.txt'}\ntest: {EX / 'par300.txt'}\nnames:\n{names}\n")
G300 = {p.stem: GT["test"][p.stem] for p in sub}; zc = VAL[CHOSEN]["estricta"]
FILES = {"pt": W, "onnx": EX / f"ecoli_gb_{CHOSEN}.onnx", "tflite_fp32": EX / f"ecoli_gb_{CHOSEN}_fp32.tflite", "tflite_fp16": EX / f"ecoli_gb_{CHOSEN}_fp16.tflite"}
i8 = sorted(p for p in EX.glob("*int8*.tflite")); FILES["tflite_int8"] = i8[0] if i8 else EX / "missing_int8.tflite"
par = {}
for tag, f in FILES.items():
    if not Path(f).exists(): par[tag] = dict(error="no existe"); continue
    try:
        mod = YOLO(str(f), task="detect"); v = mod.val(data=str(EX / "par300.yaml"), split="val", imgsz=320, conf=0.001, iou=0.7, max_det=1000, device="cpu", verbose=False, plots=False, rect=False)
        k = list(v.box.ap_class_index).index(GB); d = predict_dets(mod, sub, 320, conf=0.01, iou=zc["iou"], device="cpu", batch=1); s = summarize(match_boxes(d, G300, zc["conf"]))
        t = []; im0 = str(sub[0])
        for _ in range(5): mod.predict(im0, imgsz=320, device="cpu", verbose=False)
        for _ in range(50):
            t0 = time.perf_counter(); mod.predict(im0, imgsz=320, device="cpu", verbose=False); t.append((time.perf_counter() - t0) * 1000)
        par[tag] = dict(mAP50_Gb=float(v.box.ap50[k]), **{x: s[x] for x in ("precision", "recall", "f1", "mae")}, lat_ms_mediana_e2e=float(np.median(t)), mb=round(Path(f).stat().st_size / 1e6, 2))
    except Exception as e: par[tag] = dict(error=repr(e)[:300])
    print(tag, par[tag], flush=True)
(OUT / "paridad_300_test.json").write_text(json.dumps(par, indent=1))
io = {}
try:
    import tensorflow as tf
    for tag in ("tflite_fp32", "tflite_fp16", "tflite_int8"):
        if not Path(FILES[tag]).exists(): continue
        it = tf.lite.Interpreter(model_path=str(FILES[tag])); it.allocate_tensors()
        io[tag] = dict(inputs=[dict(shape=d["shape"].tolist(), dtype=d["dtype"].__name__, quant=[float(d["quantization"][0]), int(d["quantization"][1])]) for d in it.get_input_details()],
                       outputs=[dict(shape=d["shape"].tolist(), dtype=d["dtype"].__name__, quant=[float(d["quantization"][0]), int(d["quantization"][1])]) for d in it.get_output_details()])
except Exception as e: io["error"] = repr(e)[:300]
print(json.dumps(io, indent=1)); (OUT / "tflite_io.json").write_text(json.dumps(io, indent=1))

# %% [markdown]
# ## Artefactos y sha256
# %%
for f in EX.glob("*"):
    if f.is_file() and f.suffix in (".pt", ".onnx", ".tflite", ".txt", ".yaml"): shutil.copy2(f, OUT / f.name)
sha = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.glob("ecoli_gb_*") if p.suffix in (".pt", ".onnx", ".tflite")}
(OUT / "sha256_pesos.json").write_text(json.dumps(sha, indent=1)); print(json.dumps(sha, indent=1))
print(subprocess.run("pip freeze | grep -i -E '^(ultralytics|torch|tensorflow|onnx|onnx2tf|onnxruntime|numpy|ai-edge)'", shell=True, capture_output=True, text=True).stdout)
(OUT / "pip_freeze_final.txt").write_text(subprocess.run("pip freeze | grep -i -E '^(ultralytics|torch|tensorflow|onnx|onnx2tf|onnxruntime|numpy|ai-edge|opencv|scikit|scipy|pandas)'", shell=True, capture_output=True, text=True).stdout)
