# %% [markdown]
# # Verificar la geometria del letterbox en imagenes NO cuadradas: recorte alrededor de una deteccion; la caja del recorte, movida al marco original, debe coincidir con la de la imagen completa
# %%
import json, subprocess
subprocess.run("pip install -q ai-edge-litert", shell=True)
from pathlib import Path
import numpy as np, cv2
DSR = next(Path("/kaggle/input").glob("**/yolo_ds")); F = next(Path("/kaggle/input").glob("**/ecoli_gb_E3s43_352_fp16.tflite")); APP = json.loads(next(Path("/kaggle/input").glob("**/app_config.json")).read_text())
it = cargar_interprete(str(F)); rs = np.random.default_rng(3); imgs = sorted((DSR / "images" / "test").glob("*.jpg")); rows = []
shapes = [(480, 360), (300, 450), (560, 240), (400, 400), (640, 200)]
for p in rs.permutation(len(imgs)):
    if len(rows) >= 60: break
    img = cv2.imread(str(imgs[p])); full = [d for d in detectar(it, img, APP, "estricta")["detecciones"] if d["clase"] == "G-bacilli"]
    if not full: continue
    d = full[int(rs.integers(len(full)))]; x1, y1, x2, y2 = d["caja"]; cw, ch = shapes[len(rows) % len(shapes)]
    x0 = int(np.clip((x1 + x2) / 2 - cw * rs.uniform(0.3, 0.7), 0, 640 - cw)); y0 = int(np.clip((y1 + y2) / 2 - ch * rs.uniform(0.3, 0.7), 0, 640 - ch))
    if not (x1 > x0 + 10 and x2 < x0 + cw - 10 and y1 > y0 + 10 and y2 < y0 + ch - 10): continue
    crop = img[y0:y0 + ch, x0:x0 + cw]; det = [e for e in detectar(it, crop, APP, "estricta")["detecciones"] if e["clase"] == "G-bacilli"]
    exp = np.array([x1 - x0, y1 - y0, x2 - x0, y2 - y0]); best = (0, None)
    for e in det:
        b = np.array(e["caja"]); iw = max(0, min(b[2], exp[2]) - max(b[0], exp[0])); ih = max(0, min(b[3], exp[3]) - max(b[1], exp[1])); i = iw * ih; u = (b[2]-b[0])*(b[3]-b[1]) + (exp[2]-exp[0])*(exp[3]-exp[1]) - i
        if i / u > best[0]: best = (i / u, b)
    ce = float(np.linalg.norm(((best[1][:2] + best[1][2:]) - (exp[:2] + exp[2:])) / 2)) if best[1] is not None else None
    rows.append(dict(imagen=imgs[p].name, recorte=[cw, ch], iou=float(best[0]), error_centro_px=ce, detectada=bool(best[0] > 0)))
iou = np.array([r["iou"] for r in rows]); res = dict(n=len(rows), con_deteccion_en_recorte=int((iou > 0).sum()), iou_mayor_0_7=int((iou > 0.7).sum()), iou_mediana=float(np.median(iou)), error_centro_mediano_px=float(np.median([r["error_centro_px"] for r in rows if r["error_centro_px"] is not None])), formas=[list(s) for s in shapes])
print(json.dumps(res, indent=1)); Path("/kaggle/working/outputs").mkdir(exist_ok=True); Path("/kaggle/working/outputs/geometria_no_cuadradas.json").write_text(json.dumps(dict(resumen=res, casos=rows), indent=1, default=float))
