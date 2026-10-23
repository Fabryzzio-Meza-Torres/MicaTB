# %% [markdown]
# # Cruce de clases 640DataSet vs DetectionDataSet (mismas cajas, IoU 1.0)
# %%
import subprocess, zipfile, json, hashlib
from pathlib import Path
import pandas as pd, numpy as np
Z = Path("/kaggle/temp/z.zip"); R = Path("/kaggle/temp/raw"); R.mkdir(parents=True, exist_ok=True)
subprocess.run(["wget", "-q", "-O", str(Z), "https://zenodo.org/records/10526360/files/DeepDataSet.zip?download=1"], check=True)
assert hashlib.md5(Z.read_bytes()).hexdigest() == "04173c5da686f76fbceb118e78f1607b"
with zipfile.ZipFile(Z) as z:
    for n in z.namelist():
        if n.startswith(("DeepDataSet/640DataSet/", "640DataSet/")) and n.endswith(".txt") or "DetectionDataSet/labels" in n and n.endswith(".txt"): z.extract(n, R)
print([p.name for p in R.rglob("*") if p.is_dir()][:10])
D = next(R.rglob("DetectionDataSet")); S = next(R.rglob("640DataSet"))
print("ejemplo 640:", next(S.rglob("*.txt")).read_text()[:300])
txt = {p.stem: p for p in S.rglob("*.txt")}
def box(q):
    q = q.split(); c = int(float(q[0]))
    if len(q) == 5: xc, yc, w, h = map(float, q[1:]); return c, round(xc, 3), round(yc, 3)
    xs, ys = np.array(q[1::2], float), np.array(q[2::2], float); return c, round((xs.min()+xs.max())/2, 3), round((ys.min()+ys.max())/2, 3)
rows = []
for p in (D / "labels").glob("*.txt"):
    if p.stem not in txt: continue
    A = {(x, y): c for c, x, y in map(box, p.read_text().splitlines())}
    for c2, x, y in map(box, txt[p.stem].read_text().splitlines()):
        for dx in (0,):
            if (x, y) in A: rows.append((A[(x, y)], c2))
t = pd.crosstab(pd.Series([r[0] for r in rows], name="clase_Detection"), pd.Series([r[1] for r in rows], name="clase_640"))
print(t.to_string()); Path("/kaggle/working/crosstab.csv").write_text(t.to_csv())
print("n 640 clases:", sorted({r[1] for r in rows}))
yml = list(R.rglob("*.yaml")) + list(R.rglob("*.yml")) + list(R.rglob("classes.txt")); print("yamls:", yml)
