# %% [markdown]
# # Entrenamiento GPU: lista de experimentos (EXPS inyectada), evaluacion en VAL con conteo
# Test NO se toca aqui. Cada experimento: train -> val ultralytics -> cache de detecciones -> barrido de conf -> IC por grupos -> latencia CPU proxy.

# %% [markdown]
# ## Entorno y datos
# %%
import os, json, time, shutil, subprocess, pickle
from pathlib import Path
import numpy as np, pandas as pd
r_ = subprocess.run("pip install -q ultralytics", shell=True, capture_output=True, text=True); print("pip ultralytics rc", r_.returncode, r_.stderr[-400:])
import torch
pf = subprocess.run("pip freeze | grep -i -E '^(ultralytics|ultralytics-thop|torch|torchvision|tensorflow|onnx|onnx2tf|onnxruntime|numpy|tflite)'", shell=True, capture_output=True, text=True).stdout
Path("/kaggle/working").mkdir(exist_ok=True); (Path("/kaggle/working") / "pip_freeze.txt").write_text(pf); print(pf)
import ultralytics; print("ultralytics", ultralytics.__version__, "torch", torch.__version__, "cuda", torch.cuda.is_available(), torch.cuda.get_device_name(0) if torch.cuda.is_available() else "")
from ultralytics import YOLO
WORK = Path("/kaggle/working"); OUT = WORK / "outputs"; OUT.mkdir(exist_ok=True)
SRC = next(Path("/kaggle/input").glob("**/yolo_ds")); print("datos:", SRC)
DSR = Path("/kaggle/temp/yolo_ds")
if not DSR.exists(): shutil.copytree(SRC, DSR)
names = "\n".join(f"  {i}: {n}" for i, n in enumerate(CLASS_NAMES))
(DSR / "data_base.yaml").write_text(f"path: {DSR}\ntrain: images/train\nval: images/val\ntest: images/test\nnames:\n{names}\n")
(DSR / "data_bg.yaml").write_text(f"path: {DSR}\ntrain: [images/train, images/bg_train]\nval: images/val\ntest: images/test\nnames:\n{names}\n")
man = pd.read_csv(DSR / "split_manifest.csv"); GROUP_OF = dict(zip(man.name.str.replace(".jpg", "", regex=False), man.group))
VAL_IMGS = sorted((DSR / "images" / "val").glob("*.jpg")); GTS = load_gts(DSR, "val")
print("val:", len(VAL_IMGS), "imagenes")
assert not list(DSR.glob("labels/test/*.cache")) or True

# %% [markdown]
# ## Funcion de experimento
# %%
def stamp(m): print(f"[{time.strftime('%H:%M:%S')}] {m}", flush=True)

def run_exp(cfg):
    eid, imgsz, seed = cfg["id"], cfg["imgsz"], cfg.get("seed", 42)
    t0 = time.time()
    args = dict(data=str(DSR / ("data_bg.yaml" if cfg.get("bg") else "data_base.yaml")), epochs=cfg.get("epochs", 100), patience=cfg.get("patience", 25),
                batch=16, imgsz=imgsz, seed=seed, device=0, workers=cfg.get("workers", 3), flipud=0.5, fliplr=0.5, degrees=0.0,
                project=str(WORK / "runs"), name=eid, exist_ok=True, plots=False, max_det=1000, verbose=False)
    if "fraction" in cfg: args["fraction"] = cfg["fraction"]
    args.update(cfg.get("extra", {}))
    stamp("train start"); model = YOLO(cfg.get("weights", "yolov8n.pt")); model.train(**args); stamp("train done")
    t_train = time.time() - t0
    best = WORK / "runs" / eid / "weights" / "best.pt"; shutil.copy2(best, OUT / f"{eid}_best.pt")
    shutil.copy2(WORK / "runs" / eid / "results.csv", OUT / f"{eid}_results.csv")
    m = YOLO(str(best)); v = m.val(data=str(DSR / "data_base.yaml"), split="val", imgsz=imgsz, conf=0.001, iou=0.7, max_det=1000, device=0, verbose=False, plots=False)
    per = {}
    for k, c in enumerate(v.box.ap_class_index):
        per[CLASS_NAMES[c]] = dict(ap50=float(v.box.ap50[k]), p=float(v.box.p[k]), r=float(v.box.r[k]))
    stamp("val ultralytics done"); dets = predict_dets(m, VAL_IMGS, imgsz, conf=0.01, iou=NMS_IOU, device=0)
    pickle.dump(dets, open(OUT / f"{eid}_val_dets.pkl", "wb"))
    sw = {}
    for nm, cl in (("estricta", [GB]), ("amplia", [GB, GPB])):
        s = sweep(dets, GTS, cl); s.to_csv(OUT / f"{eid}_sweep_{nm}.csv", index=False); sw[nm] = s
    sg = sw["estricta"]; row_mae = sg.loc[sg.mae.idxmin()]; row_f1 = sg.loc[sg.f1_Gb.idxmax()]
    conf_c = float(row_mae.conf); ms = match_stats(dets, GTS, conf_c)
    ci = boot_ci_groups(dets, GTS, conf_c, [GB], GROUP_OF)
    stamp("sweep done"); lat = cpu_latency_ms(m, str(VAL_IMGS[0]), imgsz, n=30, warm=5); stamp("latency done")
    rec = dict(id=eid, cambio=cfg["cambio"], semilla=seed, imgsz=imgsz, epochs_pedidas=args["epochs"], epochs_hechas=int(len(pd.read_csv(OUT / f"{eid}_results.csv"))),
               mAP50_Gb=per.get("G-bacilli", {}).get("ap50"), mAP50_todas=float(v.box.map50), mAP50_95_todas=float(v.box.map),
               conf_mae_opt=conf_c, mae_Gb=float(row_mae.mae), mae_ci_lo=ci[0], mae_ci_hi=ci[1], bias=float(row_mae.bias),
               P_Gb=ms["precision"], R_Gb=ms["recall"], F1_Gb=ms["f1"], Gb_pred_como_Gpb=ms["gt_clase_predicha_como_otra"],
               conf_f1_opt=float(row_f1.conf), f1_opt=float(row_f1.f1_Gb), mae_amplia=float(sw["amplia"].mae.min()),
               lat_cpu_ms_pt=lat, params=int(sum(p.numel() for p in m.model.parameters())), min_train=round(t_train / 60, 1), pesos=f"{eid}_best.pt")
    (OUT / f"{eid}_metrics.json").write_text(json.dumps(dict(rec=rec, per_class=per), indent=1))
    pd.DataFrame([rec]).to_csv(OUT / "experiments.csv", mode="a", header=not (OUT / "experiments.csv").exists(), index=False)
    print(json.dumps(rec, indent=1)); print(json.dumps(per, indent=1))
    if cfg.get("export_test"):
        for tag, kw in (("onnx", "format='onnx'"), ("tflite_fp16", "format='tflite', half=True"), ("tflite_int8", f"format='tflite', int8=True, data='{DSR / 'data_base.yaml'}'")):
            code = f"from ultralytics import YOLO; print('EXPORT OK', YOLO('{best}').export(imgsz={imgsz}, {kw}))"
            t1 = time.time()
            try:
                r = subprocess.run(["python", "-c", code], capture_output=True, text=True, timeout=900)
                print(f"[{tag}] rc={r.returncode} {time.time()-t1:.0f}s", (r.stdout + r.stderr)[-700:], flush=True)
            except subprocess.TimeoutExpired:
                print(f"[{tag}] TIMEOUT 900s", flush=True)
    shutil.rmtree(WORK / "runs" / eid / "weights", ignore_errors=True) if not cfg.get("export_test") else None
    return rec

# %% [markdown]
# ## Ejecutar experimentos
# %%
status = {"done": [], "failed": []}
for cfg in EXPS:
    try:
        run_exp(cfg); status["done"].append(cfg["id"])
    except Exception as e:
        import traceback; status["failed"].append((cfg["id"], repr(e))); print(traceback.format_exc())
    (OUT / "status.json").write_text(json.dumps(status))
print(status)
