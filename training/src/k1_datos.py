# %% [markdown]
# # Paso 1 y 2. Datos: grupos (union-find), split sin leakage, EDA, fondos y baselines
# Dataset: Clinical Bacteria DataSet (Wang X., 2024), Zenodo 10.5281/zenodo.10526360 v3, CC BY 4.0.
# Salidas: /kaggle/working/yolo_ds (imagenes+labels+yaml+manifest) y /kaggle/working/outputs (tablas, figuras).

# %% [markdown]
# ## Imports y configuracion
# %%
import os, re, json, random, hashlib, subprocess, zipfile, shutil, itertools, time
from pathlib import Path
import numpy as np, pandas as pd, cv2
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from scipy.stats import ks_2samp
from sklearn.model_selection import StratifiedGroupKFold

T0 = time.time()
SEED = 42
random.seed(SEED); np.random.seed(SEED)
TEMP = Path("/kaggle/temp"); WORK = Path("/kaggle/working")
RAW = TEMP / "raw"; DS = WORK / "yolo_ds"; OUT = WORK / "outputs"
for p in (TEMP, DS, OUT, OUT / "eda"): p.mkdir(parents=True, exist_ok=True)
CLASS_NAMES = ["G-cocci", "G+cocci", "G-bacilli", "G+bacilli"]
GB, GPB = 2, 3
GROUP_REGEX = r"^(\d{6})"
CROP_REGEX = r"^\d{6}_\d+_\d+$"
ZENODO_URL = "https://zenodo.org/records/10526360/files/DeepDataSet.zip?download=1"
EXPECTED_MD5 = "04173c5da686f76fbceb118e78f1607b"
ZIP_PATH = TEMP / "DeepDataSet.zip"
progress = {"status": "running", "stage": "start"}
def save_progress():
    (WORK / "run_summary.json").write_text(json.dumps(progress, indent=2, default=str))
def md5sum(path, chunk=1 << 22):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(chunk), b""): h.update(b)
    return h.hexdigest()
def sha256sum(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def hdr(t): print(f"\n===== {t} =====")
pf = subprocess.run("pip freeze | grep -i -E '^(ultralytics|torch|tensorflow|onnx|onnx2tf|numpy|scikit-learn|opencv)'", shell=True, capture_output=True, text=True).stdout
(OUT / "pip_freeze_k1.txt").write_text(pf); print(pf)

# %% [markdown]
# ## Descarga verificada (md5) y extraccion
# %%
if not RAW.exists() or not any(RAW.iterdir()):
    subprocess.run(["wget", "-q", "-O", str(ZIP_PATH), ZENODO_URL], check=True)
    got = md5sum(ZIP_PATH); assert got == EXPECTED_MD5, f"md5 distinto: {got}"
    RAW.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ZIP_PATH) as z: z.extractall(RAW)
    ZIP_PATH.unlink()
print("listo", RAW, [p.name for p in RAW.iterdir()])

# %% [markdown]
# ## Inventario y huellas (md5, dHash64, brillo, Lab a/b, nitidez)
# Totales por clase se reconfirman con las cajas completas.
# %%
SUB = RAW / "DetectionDataSet"
LISTS = {}
for p in (RAW / "SegmentationDataSet" / "txt").glob("*.txt"):
    s = p.stem.lower()
    if s in ("train", "val", "test"): LISTS.update({Path(x).stem: s for x in p.read_text().split()})
imgs = sorted((SUB / "images").glob("*.jpg"))
df = pd.DataFrame({"name": [p.name for p in imgs], "stem": [p.stem for p in imgs], "img_path": [str(p) for p in imgs]})
df["label_path"] = [str(SUB / "labels" / (s + ".txt")) for s in df.stem]
assert all(Path(p).exists() for p in df.label_path)
df["split_orig"] = df.stem.map(LISTS)

def dhash64(gray):
    s = cv2.resize(gray, (9, 8), interpolation=cv2.INTER_AREA)
    return int(np.packbits((s[:, 1:] > s[:, :-1]).flatten()).view(">u8")[0])

recs = []
for r in df.itertuples():
    im = cv2.imread(r.img_path); g = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY); lab = cv2.cvtColor(im, cv2.COLOR_BGR2LAB)
    recs.append(dict(md5=md5sum(r.img_path), dhash=dhash64(g), H=im.shape[0], W=im.shape[1], gray=float(g.mean()),
                     lab_a=float(lab[..., 1].mean()), lab_b=float(lab[..., 2].mean()), blur=float(cv2.Laplacian(g, cv2.CV_64F).var())))
df = df.join(pd.DataFrame(recs, index=df.index)); df["dhash"] = df.dhash.astype(np.uint64)
rows = []
for r in df.itertuples():
    for line in Path(r.label_path).read_text().splitlines():
        p = line.split()
        if len(p) == 5: rows.append((r.Index, int(p[0]), *map(float, p[1:])))
bx = pd.DataFrame(rows, columns=["img_idx", "cls", "xc", "yc", "w", "h"])
assert bx.cls.between(0, 3).all()
df["group_id"] = df.name.str.extract(GROUP_REGEX, expand=False)
df["pattern"] = np.where(df.stem.str.match(CROP_REGEX), "crop", "simple")
for c in range(4): df[f"n_c{c}"] = bx[bx.cls == c].groupby("img_idx").size().reindex(df.index).fillna(0).astype(int)
df["n_boxes"] = df[[f"n_c{c}" for c in range(4)]].sum(1)
df["dom"] = df[[f"n_c{c}" for c in range(4)]].to_numpy().argmax(1)
print(len(df), "imagenes", len(bx), "cajas; H,W unicos:", df.H.unique(), df.W.unique())
tot = bx.cls.value_counts().sort_index().tolist()
print("cajas por clase (esperado 3371,1462,5799,1192):", tot, "suma", sum(tot))
print("imagen(es) sin lista de autores:", df[df.split_orig.isna()].stem.tolist())
print("G-bacilli en listas de autores:", int(df[df.split_orig.notna()].n_c2.sum()), "| en imagen sin lista:", int(df[df.split_orig.isna()].n_c2.sum()))
print(df.pattern.value_counts().to_dict())
progress.update(stage="inventario", n_imgs=len(df), n_boxes=len(bx), box_totals=tot); save_progress()

# %% [markdown]
# ## 1.3 Duplicados exactos: se deja un archivo por md5
# Los md5 identicos viven siempre en el mismo grupo, asi que se deduplica antes de agrupar (equivalente a dejarlo dentro de cada split). Si dos copias tienen labels distintos se registra.
# %%
dups = []
keep = []
for m, g in df.groupby("md5"):
    keep.append(g.index[0])
    if len(g) > 1:
        labs = {tuple(sorted(Path(p).read_text().split("\n"))) for p in g.label_path}
        dups.append(dict(md5=m, n=len(g), names=";".join(g.name), labels_distintos=len(labs) > 1))
dups = pd.DataFrame(dups)
print("grupos md5 duplicados:", len(dups), "| con labels distintos:", int(dups.labels_distintos.sum()) if len(dups) else 0, "| imagenes eliminadas:", len(df) - len(keep))
dups.to_csv(OUT / "md5_duplicados.csv", index=False)
df_all = df.copy()
df = df.loc[sorted(keep)].reset_index(drop=True)
bx = bx[bx.img_idx.isin(keep)]
# reindexar cajas al nuevo indice
old2new = {old: new for new, old in enumerate(sorted(keep))}
bx = bx.assign(img_idx=bx.img_idx.map(old2new)).reset_index(drop=True)
print("tras dedupe md5:", len(df), "imagenes", len(bx), "cajas")
progress.update(md5_groups=len(dups), after_md5=len(df)); save_progress()

# %% [markdown]
# ## 1.1 Grupos: union-find por id de origen y dHash <= 3 (todas contra todas)
# Si alguna componente supera el 2 % del total se rompe con umbral dHash mas estricto (3 -> 2 -> 1 -> 0).
# %%
N = len(df)
H8 = df.dhash.to_numpy(np.uint64).view(np.uint8).reshape(-1, 8)
LUT = np.array([bin(i).count("1") for i in range(256)], np.uint8)
ham = np.zeros((N, N), np.uint8)
for i in range(N): ham[i] = LUT[np.bitwise_xor(H8, H8[i])].sum(1)
iu = np.triu_indices(N, 1)
dist_pairs = ham[iu]
print("pares con dHash<=3:", int((dist_pairs <= 3).sum()), "| dist 0:", int((dist_pairs == 0).sum()))

def components(thr):
    par = list(range(N))
    def find(x):
        while par[x] != x: par[x] = par[par[x]]; x = par[x]
        return x
    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb: par[ra] = rb
    for ids, g in df.groupby("group_id").groups.items():
        g = list(g)
        for a in g[1:]: union(g[0], a)
    sel = np.where(dist_pairs <= thr)[0]
    for a, b in zip(iu[0][sel], iu[1][sel]): union(int(a), int(b))
    return np.array([find(i) for i in range(N)])

used_thr = None
for thr in (3, 2, 1, 0):
    comp = components(thr); sizes = pd.Series(comp).value_counts()
    print(f"dHash<={thr}: componentes {len(sizes)}, mayor {sizes.iloc[0]} ({100 * sizes.iloc[0] / N:.2f} %)")
    used_thr = thr
    if sizes.iloc[0] <= 0.02 * N: break
df["group"] = pd.Series(comp).map({c: f"g{k:04d}" for k, c in enumerate(sizes.index)}).values
sz = df.group.value_counts()
print("distribucion de tamanos de componente:\n", sz.describe().to_string())
print("tamanos (top 10):", sz.head(10).tolist(), "| componentes de 1:", int((sz == 1).sum()))
pd.DataFrame({"size": sz.values}).value_counts("size").sort_index().to_csv(OUT / "tamanos_componentes.csv")
progress.update(dhash_thr_used=used_thr, n_groups=int(sz.size), biggest_comp=int(sz.iloc[0])); save_progress()

# figura: 30 pares casi duplicados al azar (con ids distintos, que son los que el id no atrapa)
rng = np.random.default_rng(SEED)
sel = np.where((dist_pairs <= 3))[0]
cand = [(int(iu[0][k]), int(iu[1][k]), int(dist_pairs[k])) for k in sel]
cross_id = [c for c in cand if df.group_id[c[0]] != df.group_id[c[1]]]
print("pares dHash<=3:", len(cand), "| con ids distintos:", len(cross_id))
pick = [cross_id[i] for i in rng.choice(len(cross_id), min(30, len(cross_id)), replace=False)] if cross_id else []
fig, axs = plt.subplots(6, 10, figsize=(30, 18))
for k, (a, b, d) in enumerate(pick):
    r, c = divmod(k, 5)
    for off, i in enumerate((a, b)):
        ax = axs[r, c * 2 + off]; ax.imshow(cv2.cvtColor(cv2.imread(df.img_path[i]), cv2.COLOR_BGR2RGB)); ax.axis("off")
        ax.set_title(f"{df.name[i][:14]} d={d}", fontsize=9)
for ax in axs.ravel(): ax.axis("off")
plt.tight_layout(); plt.savefig(OUT / "near_dup_pairs_30.png", dpi=55); plt.close()
pd.DataFrame([(df.name[a], df.name[b], d) for a, b, d in pick], columns=["a", "b", "dhash"]).to_csv(OUT / "near_dup_pairs_30.csv", index=False)

# %% [markdown]
# ## 1.2 Split: StratifiedGroupKFold(20) con 20 semillas, fold->split 14/3/3
# Estrato = clase dominante x patron (crop/simple). Se prueban 50 permutaciones de folds por semilla y se elige la de menor desviacion de proporciones (solo mira conteos, no rendimiento).
# %%
strat = (df.dom * 2 + (df.pattern == "crop").astype(int)).to_numpy()
box_c = df[[f"n_c{c}" for c in range(4)]].to_numpy()
is_crop = (df.pattern == "crop").to_numpy()
TARGET = np.array([0.70, 0.15, 0.15])

def evaluate(split_of_img):  # 0 train,1 val,2 test
    ni = np.array([(split_of_img == s).sum() for s in range(3)]) / N
    nb = np.array([box_c[split_of_img == s].sum(0) for s in range(3)])   # 3 x 4
    share = nb / nb.sum(0)
    crop = np.array([is_crop[split_of_img == s].mean() for s in range(3)])
    return ni, nb, share, crop

def score(ni, share):
    return float(np.abs(ni - TARGET).max() + np.abs(share[1:] - 0.15).max())

def criteria(nb, share, crop, lo, hi):
    d1 = bool(((share[1:] >= lo) & (share[1:] <= hi)).all()); d2 = bool((nb[1:, GPB] >= 100).all())
    e = bool((np.abs(crop[1:] - crop[0]) < 0.05).all())
    return d1 and d2, e

best = {}
for lo, hi in ((0.12, 0.18), (0.10, 0.20)):
    cands = []
    for sd in range(20):
        fold = np.zeros(N, int)
        for k, (_, te) in enumerate(StratifiedGroupKFold(20, shuffle=True, random_state=sd).split(np.zeros(N), strat, df.group)): fold[te] = k
        r = np.random.default_rng(1000 + sd)
        for _ in range(50):
            perm = r.permutation(20); sof = np.zeros(20, int); sof[perm[14:17]] = 1; sof[perm[17:]] = 2
            s_img = sof[fold]; ni, nb, share, crop = evaluate(s_img)
            dok, eok = criteria(nb, share, crop, lo, hi)
            if dok and eok: cands.append((score(ni, share), sd, s_img.copy()))
    print(f"rango {lo}-{hi}: candidatos que cumplen d) y e): {len(cands)}")
    if cands:
        cands.sort(key=lambda t: t[0]); best = dict(score=cands[0][0], seed=cands[0][1], split=cands[0][2], range=(lo, hi)); break
assert best, "ningun split cumple d) y e) ni con rango relajado"
df["split"] = np.array(["train", "val", "test"])[best["split"]]
RELAXED = best["range"] != (0.12, 0.18)
print("semilla elegida", best["seed"], "score", round(best["score"], 4), "rango d):", best["range"], "RELAJADO" if RELAXED else "")
progress.update(split_seed=best["seed"], split_score=best["score"], d_range=best["range"], d_relaxed=RELAXED)

# %% [markdown]
# ## Verificacion de criterios a) a e)
# %%
ni, nb, share, crop = evaluate(best["split"])
a_ok = bool((df.groupby("group").split.nunique() == 1).all())
b_ok = bool((df.groupby("md5").split.nunique() == 1).all())
sp = df.split.to_numpy(); mask = (dist_pairs <= 3) & (sp[iu[0]] != sp[iu[1]])
n_cross = int(mask.sum()); c_ok = n_cross == 0
lo, hi = best["range"]
d_share = bool(((share[1:] >= lo) & (share[1:] <= hi)).all()); d_gpb = bool((nb[1:, GPB] >= 100).all())
e_ok = bool((np.abs(crop[1:] - crop[0]) < 0.05).all())
crit = pd.DataFrame([
    ("a) ningun grupo en dos splits", a_ok, ""),
    ("b) cero md5 identicos entre splits", b_ok, ""),
    (f"c) cero pares dHash<=3 entre splits (n={n_cross}; umbral de union usado {used_thr})", c_ok, ""),
    (f"d1) val y test con {lo:.0%}-{hi:.0%} de cajas por clase", d_share, f"min {share[1:].min():.3f} max {share[1:].max():.3f}"),
    ("d2) >=100 cajas G+bacilli en val y test", d_gpb, f"val {nb[1, GPB]} test {nb[2, GPB]}"),
    ("e) crop/simple a <5 pts de train", e_ok, f"crop frac train/val/test = {crop.round(3).tolist()}")], columns=["criterio", "cumple", "detalle"])
print(crit.to_string()); crit.to_csv(OUT / "criterios_split.csv", index=False)
tab = pd.DataFrame(nb.T, index=CLASS_NAMES, columns=["train", "val", "test"]); tab.loc["IMAGENES"] = [(df.split == s).sum() for s in ("train", "val", "test")]
tab.loc["TOTAL cajas"] = nb.sum(1); print(tab.to_string()); tab.to_csv(OUT / "tabla_split_clases.csv")
print("proporcion cajas por clase (train/val/test):\n", pd.DataFrame(share.T, index=CLASS_NAMES, columns=["train", "val", "test"]).round(3).to_string())
progress["criterios"] = crit.to_dict("records"); save_progress()
assert a_ok and b_ok and c_ok, "STOP: fallan criterios a/b/c -> consultar al usuario"

# manifest congelado
man = df[["name", "group", "pattern", "split", "md5"]]
man.to_csv(OUT / "split_manifest.csv", index=False)
progress["manifest_sha256"] = sha256sum(OUT / "split_manifest.csv"); print("sha256 manifest:", progress["manifest_sha256"]); save_progress()

# %% [markdown]
# ## 3.5 Comparacion de labels 640DataSet vs DetectionDataSet (por nombre comun)
# %%
cmp = {"status": "no_ejecutado"}
t_c = time.time()
try:
    D640 = RAW / "640DataSet"
    txt = {p.stem: p for p in D640.rglob("*.txt")}
    print("txt en 640:", len(txt), "stems en comun con Detection:", len(set(txt) & set(df_all.stem)))
    def read_boxes(p):
        out = []
        for line in Path(p).read_text().splitlines():
            q = line.split()
            if len(q) == 5: c, xc, yc, w, h = int(float(q[0])), *map(float, q[1:])
            elif len(q) >= 7 and len(q) % 2 == 1:
                xs, ys = np.array(q[1::2], float), np.array(q[2::2], float)
                c, xc, yc, w, h = int(float(q[0])), (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2, np.ptp(xs), np.ptp(ys)
            else: continue
            out.append((c, xc - w / 2, yc - h / 2, xc + w / 2, yc + h / 2))
        return out
    def iou1(a, b):
        iw = max(0, min(a[3], b[3]) - max(a[1], b[1])); ih = max(0, min(a[4], b[4]) - max(a[2], b[2])); i = iw * ih
        u = (a[3]-a[1])*(a[4]-a[2]) + (b[3]-b[1])*(b[4]-b[2]) - i
        return i / u if u > 0 else 0
    n_det = n_640 = matched = cls_chg = cls_chg_gb = imgs_cmp = imgs_diff = 0; ious = []
    for st in df_all.stem:
        if st not in txt: continue
        A, B = read_boxes(SUB / "labels" / (st + ".txt")), read_boxes(txt[st]); imgs_cmp += 1
        n_det += len(A); n_640 += len(B)
        ua, ub, m = set(), set(), 0
        for v, i, j in sorted(((iou1(a, b), i, j) for i, a in enumerate(A) for j, b in enumerate(B)), reverse=True):
            if v <= 0.5: break
            if i in ua or j in ub: continue
            ua.add(i); ub.add(j); m += 1; ious.append(v)
            if A[i][0] != B[j][0]: cls_chg += 1; cls_chg_gb += GB in (A[i][0], B[j][0])
        matched += m; imgs_diff += (m != len(A)) or (m != len(B))
        if time.time() - t_c > 1200: print("corte por tiempo (20 min)"); break
    cmp = dict(status="ok", imagenes_comparadas=imgs_cmp, imagenes_con_diferencia=imgs_diff, cajas_detection=n_det, cajas_640=n_640,
               emparejadas=matched, faltantes_en_640=n_det - matched, extra_en_640=n_640 - matched, cambios_de_clase=cls_chg,
               cambios_que_involucran_Gbacilli=cls_chg_gb, iou_medio=float(np.mean(ious)) if ious else None)
except Exception as e:
    cmp = dict(status="error", error=repr(e))
print(json.dumps(cmp, indent=1)); (OUT / "label_comparison.json").write_text(json.dumps(cmp, indent=1))
progress["label_comparison"] = cmp; save_progress()

# %% [markdown]
# ## Escribir yolo_ds (imagenes y labels por split) y splits.csv
# %%
for s in ("train", "val", "test"):
    (DS / "images" / s).mkdir(parents=True, exist_ok=True); (DS / "labels" / s).mkdir(parents=True, exist_ok=True)
for r in df.itertuples():
    shutil.copy2(r.img_path, DS / "images" / r.split / r.name)
    shutil.copy2(r.label_path, DS / "labels" / r.split / (r.stem + ".txt"))
df.drop(columns=["img_path", "label_path"]).assign(dhash=df.dhash.astype(str)).to_csv(DS / "splits.csv", index=False)
shutil.copy2(OUT / "split_manifest.csv", DS / "split_manifest.csv")
for s in ("train", "val", "test"): assert len(list((DS / "images" / s).glob("*.jpg"))) == (df.split == s).sum()
print("yolo_ds escrito", {s: int((df.split == s).sum()) for s in ("train", "val", "test")})
(DS / "DATA.md").write_text(
    "# Procedencia\nClinical Bacteria DataSet (Wang X., 2024). Zenodo 10.5281/zenodo.10526360 v3, CC BY 4.0.\n"
    f"Archivo DeepDataSet.zip md5 {EXPECTED_MD5}. Articulo: Wang et al., Sci Data 11, 608 (2024), doi 10.1038/s41597-024-03370-5.\n"
    "Re-split por grupos (union-find id de origen + dHash) propio; ver split_manifest.csv.\n")

# %% [markdown]
# ## 1.4 EDA sobre TRAIN (val y test solo para comparar distribuciones)
# %%
E = OUT / "eda"
bx = bx.join(df[["split", "pattern"]], on="img_idx")
tr = df[df.split == "train"]; btr = bx[bx.img_idx.isin(tr.index)]
t1 = pd.crosstab(bx.cls.map(dict(enumerate(CLASS_NAMES))), bx.split)[["train", "val", "test"]]; t1.to_csv(E / "cajas_por_clase_split.csv"); print(t1.to_string())
t2 = tr.groupby("pattern")[["n_boxes", "n_c0", "n_c1", "n_c2", "n_c3"]].describe().T; t2.to_csv(E / "cajas_por_imagen_patron_train.csv")
print("cajas/imagen train por patron:\n", tr.groupby("pattern").n_boxes.describe().to_string())
print("cajas/imagen por clase dominante:\n", tr.groupby("dom").n_boxes.describe().rename(index=dict(enumerate(CLASS_NAMES))).to_string())
# tamano de caja a 640/416/320: fraccion bajo 8/12/16 px (lado menor)
rows = []
for c in range(4):
    g = btr[btr.cls == c]
    for res in (640, 416, 320):
        m = np.minimum(g.w, g.h) * res; mx = np.maximum(g.w, g.h) * res
        rows.append(dict(clase=CLASS_NAMES[c], res=res, n=len(g), lado_min_mediano=float(m.median()), lado_max_mediano=float(mx.median()),
                         **{f"menor_{t}px": int((m < t).sum()) for t in (8, 12, 16)}))
t3 = pd.DataFrame(rows); t3.to_csv(E / "tamano_caja_por_res.csv", index=False); print(t3.to_string())
# hacinamiento
def iou_mat(b):
    x1, y1, x2, y2 = b[:, 0], b[:, 1], b[:, 2], b[:, 3]
    iw = np.clip(np.minimum(x2[:, None], x2[None]) - np.maximum(x1[:, None], x1[None]), 0, None)
    ih = np.clip(np.minimum(y2[:, None], y2[None]) - np.maximum(y1[:, None], y1[None]), 0, None)
    i = iw * ih; a = (x2 - x1) * (y2 - y1); return i / (a[:, None] + a[None] - i + 1e-9)
crowd = []
for i, g in btr.groupby("img_idx"):
    if len(g) < 2: continue
    b = np.stack([(g.xc - g.w/2), (g.yc - g.h/2), (g.xc + g.w/2), (g.yc + g.h/2)], 1) * 640
    m = iou_mat(b); np.fill_diagonal(m, 0)
    cc = np.stack([g.xc, g.yc], 1) * 640; dd = np.linalg.norm(cc[:, None] - cc[None], axis=2); np.fill_diagonal(dd, 1e9)
    crowd.append(dict(img=i, n=len(g), max_iou=float(m.max()), nn_dist=float(dd.min(1).mean())))
crowd = pd.DataFrame(crowd)
print(f"hacinamiento train: imagenes con >=2 cajas {len(crowd)} / {len(tr)}; IoU max mediano {crowd.max_iou.median():.3f}; frac IoU>0.3: {(crowd.max_iou > 0.3).mean():.3f}; dist centros vecino mediana {crowd.nn_dist.median():.1f}px")
crowd.describe().to_csv(E / "hacinamiento.csv")
# co-ocurrencia
pres = (tr[[f"n_c{c}" for c in range(4)]].to_numpy() > 0).astype(int); co = pd.DataFrame(pres.T @ pres, index=CLASS_NAMES, columns=CLASS_NAMES)
co.to_csv(E / "coocurrencia.csv"); print("co-ocurrencia (imagenes con ambas clases):\n", co.to_string())
# bordes y posicion
eps = 2 / 640
btr = btr.assign(cut=((btr.xc - btr.w/2) <= eps) | ((btr.yc - btr.h/2) <= eps) | ((btr.xc + btr.w/2) >= 1 - eps) | ((btr.yc + btr.h/2) >= 1 - eps))
print("cajas tocando borde (<=2px) por patron:", btr.groupby("pattern").cut.mean().round(3).to_dict(), "| por clase:", btr.groupby("cls").cut.mean().round(3).to_dict())
fig, ax = plt.subplots(1, 4, figsize=(16, 4))
for c in range(4):
    g = btr[btr.cls == c]; ax[c].hist2d(g.xc, g.yc, bins=16, range=[[0, 1], [0, 1]]); ax[c].invert_yaxis(); ax[c].set_title(CLASS_NAMES[c])
plt.tight_layout(); plt.savefig(E / "centros_por_clase.png", dpi=70); plt.close()
# cambio de dominio: KS contra train
feat = ["gray", "lab_a", "lab_b", "blur"]; ks = []
for f in feat:
    for s in ("val", "test"):
        r = ks_2samp(df[df.split == "train"][f], df[df.split == s][f]); ks.append(dict(feature=f, vs="train", split=s, KS=r.statistic, p=r.pvalue))
    for p_ in ("crop", "simple"):
        r = ks_2samp(df[df.pattern == p_][f], df[df.pattern != p_][f]); ks.append(dict(feature=f, vs="otro patron", split=p_, KS=r.statistic, p=r.pvalue))
ks = pd.DataFrame(ks); ks.to_csv(E / "ks_dominio.csv", index=False); print(ks.round(4).to_string())
for by in ("pattern", "dom", "split"):
    t = df.groupby(by)[feat].agg(["mean", "std", "median"]).round(2); t.to_csv(E / f"estadisticos_por_{by}.csv"); print(f"por {by}:\n", t.to_string())
fig, ax = plt.subplots(1, 4, figsize=(18, 4))
for k, f in enumerate(feat):
    for s in ("train", "val", "test"): ax[k].hist(df[df.split == s][f], bins=40, alpha=.4, density=True, label=s, range=(0, 120) if f == "blur" else None)
    ax[k].set_title(f); ax[k].legend()
plt.tight_layout(); plt.savefig(E / "dominio_hist.png", dpi=70); plt.close()
# revision visual: 15 por clase dominante
COL = [(255, 128, 0), (0, 160, 255), (255, 0, 90), (0, 200, 0)]
def draw(i):
    im = cv2.cvtColor(cv2.imread(df.img_path[i]), cv2.COLOR_BGR2RGB)
    for r in bx[bx.img_idx == i].itertuples():
        x1, y1, x2, y2 = [int(v * 640) for v in (r.xc - r.w/2, r.yc - r.h/2, r.xc + r.w/2, r.yc + r.h/2)]
        cv2.rectangle(im, (x1, y1), (x2, y2), COL[r.cls], 2)
    return im
rng = np.random.default_rng(SEED)
for c in range(4):
    pool = tr.index[tr.dom == c].to_numpy(); ch = rng.choice(pool, 15, replace=False)
    fig, axs = plt.subplots(3, 5, figsize=(20, 12.5))
    for ax, i in zip(axs.ravel(), ch): ax.imshow(draw(i)); ax.set_title(f"{df.name[i]} n={df.n_boxes[i]}", fontsize=9); ax.axis("off")
    plt.suptitle(f"dominante {CLASS_NAMES[c]} (naranja G-cocci, azul G+cocci, rosa G-bacilli, verde G+bacilli)"); plt.tight_layout()
    plt.savefig(E / f"visual_dom_{CLASS_NAMES[c]}.png", dpi=60); plt.close()
progress["stage"] = "eda"; save_progress()

# %% [markdown]
# ## 1.6 Fondos sinteticos: ventanas 320x320 sin cajas (margen 20 px), mosaicos 2x2 de la misma particion
# Se generan siempre; que se usen en entrenamiento depende de la revision visual de 100 ventanas.
# %%
MARGIN, WIN = 20, 320
img_boxes = {i: g for i, g in bx.groupby("img_idx")}
def free_windows(i, W=640, H=640):
    g = img_boxes.get(i)
    bxs = np.zeros((0, 4)) if g is None else np.stack([(g.xc-g.w/2)*W-MARGIN, (g.yc-g.h/2)*H-MARGIN, (g.xc+g.w/2)*W+MARGIN, (g.yc+g.h/2)*H+MARGIN], 1)
    return [(x, y) for x in range(0, W - WIN + 1, 32) for y in range(0, H - WIN + 1, 32)
            if not ((bxs[:, 0] < x + WIN) & (bxs[:, 2] > x) & (bxs[:, 1] < y + WIN) & (bxs[:, 3] > y)).any()]
pools = {s: [(i, w) for i in df.index[df.split == s] for w in [free_windows(i)] if w] for s in ("train", "val", "test")}
print("imagenes con al menos una ventana libre:", {s: f"{len(p)}/{(df.split == s).sum()}" for s, p in pools.items()})
# 100 ventanas para revision visual (de train, val y test, al azar)
rng = np.random.default_rng(SEED); rev = []
allp = [(s, i, w) for s, p in pools.items() for i, w in p]
for k in rng.choice(len(allp), min(100, len(allp)), replace=False):
    s, i, w = allp[k]; x, y = w[rng.integers(len(w))]; rev.append((s, i, x, y))
for part in range(4):
    fig, axs = plt.subplots(5, 5, figsize=(17, 17))
    for ax, (s, i, x, y) in zip(axs.ravel(), rev[part * 25:(part + 1) * 25]):
        ax.imshow(cv2.cvtColor(cv2.imread(df.img_path[i])[y:y + WIN, x:x + WIN], cv2.COLOR_BGR2RGB)); ax.set_title(f"#{rev.index((s,i,x,y))} {s} {df.name[i][:12]}", fontsize=9); ax.axis("off")
    plt.tight_layout(); plt.savefig(E / f"ventanas_revision_{part}.png", dpi=60); plt.close()
def make_bg(split, n, outdir, rng):
    pool = pools[split]; (DS / "images" / outdir).mkdir(parents=True, exist_ok=True); (DS / "labels" / outdir).mkdir(parents=True, exist_ok=True)
    for made in range(n):
        pick = rng.choice(len(pool), 4, replace=False); canvas = np.zeros((640, 640, 3), np.uint8)
        for q, k in enumerate(pick):
            i, ws = pool[k]; x, y = ws[rng.integers(len(ws))]
            canvas[(q // 2) * 320:(q // 2) * 320 + 320, (q % 2) * 320:(q % 2) * 320 + 320] = cv2.imread(df.img_path[i])[y:y + WIN, x:x + WIN]
        name = f"bg_{split}_{made:04d}"
        cv2.imwrite(str(DS / "images" / outdir / (name + ".jpg")), canvas, [cv2.IMWRITE_JPEG_QUALITY, 95]); (DS / "labels" / outdir / (name + ".txt")).write_text("")
    return n
rng = np.random.default_rng(SEED); ntr = int((df.split == "train").sum())
bgn = {"bg_train": make_bg("train", round(0.10 * ntr), "bg_train", rng), "bg_val": make_bg("val", 150, "bg_val", rng), "bg_test": make_bg("test", 150, "bg_test", rng)}
print("fondos:", bgn)
names = "\n".join(f"  {i}: {n}" for i, n in enumerate(CLASS_NAMES))
(DS / "data_base.yaml").write_text(f"path: {DS}\ntrain: images/train\nval: images/val\ntest: images/test\nnames:\n{names}\n")
(DS / "data_bg.yaml").write_text(f"path: {DS}\ntrain: [images/train, images/bg_train]\nval: images/val\ntest: images/test\nnames:\n{names}\n")
progress.update(stage="fondos", backgrounds=bgn); save_progress()

# %% [markdown]
# ## Paso 2. Baselines de conteo (val)
# 1) mediana de train. 2) umbral en Lab (a alto = rosado) + componentes conexas, calibrado en train para minimizar MAE de G-bacilli.
# %%
va = df[df.split == "val"]
res = {}
for nm, cols in (("estricta_Gb", ["n_c2"]), ("amplia_Gb+Gpb", ["n_c2", "n_c3"])):
    y_tr = tr[cols].sum(1); y_va = va[cols].sum(1); med = float(y_tr.median())
    res[f"mediana_{nm}"] = dict(mediana_train=med, mae_val=float((y_va - med).abs().mean()))
print(res)
GRID = list(itertools.product([6, 10, 14, 20], [60, 150, 300]))
def lab_counts(i):
    im = cv2.imread(df.img_path[i]); a = cv2.cvtColor(im, cv2.COLOR_BGR2LAB)[..., 1].astype(np.int16)
    da = cv2.GaussianBlur((a - int(np.median(a))).astype(np.float32), (0, 0), 1.5); out = []
    for t, amin in GRID:
        m = cv2.morphologyEx((da > t).astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
        n, _, st, _ = cv2.connectedComponentsWithStats(m); out.append(int((st[1:, cv2.CC_STAT_AREA] >= amin).sum()))
    return out
t_b = time.time()
ctr = np.array([lab_counts(i) for i in tr.index]); cva = np.array([lab_counts(i) for i in va.index])
mae_tr = np.abs(ctr - tr.n_c2.to_numpy()[:, None]).mean(0); k = int(mae_tr.argmin())
res["umbral_Lab"] = dict(delta_a=GRID[k][0], area_min=GRID[k][1], mae_train=float(mae_tr[k]), mae_val_estricta=float(np.abs(cva[:, k] - va.n_c2).mean()),
                        mae_val_amplia=float(np.abs(cva[:, k] - va.n_c2 - va.n_c3).mean()), seg=round(time.time() - t_b))
print(res["umbral_Lab"])
(OUT / "baselines.json").write_text(json.dumps(res, indent=1)); progress["baselines"] = res

# %% [markdown]
# ## Resumen y cierre
# %%
df.drop(columns=["img_path", "label_path"]).assign(dhash=df.dhash.astype(str)).to_csv(OUT / "fingerprints.csv", index=False)
progress.update(status="complete", stage="done", minutos=round((time.time() - T0) / 60, 1)); save_progress()
print(json.dumps({k: v for k, v in progress.items() if k not in ("criterios",)}, indent=1, default=str)[:3000])
