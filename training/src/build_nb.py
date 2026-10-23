"""Uso: python build_nb.py src/x.py out_dir nombre [--lib src/eclib.py] [--set KEY=JSON ...]
Convierte un .py con marcadores '# %%' en .ipynb. '# %% [markdown]' = celda markdown (lineas '# ...')."""
import json, re, sys
from pathlib import Path

def cells(text):
    out, cur, kind = [], [], "code"
    def flush():
        body = "\n".join(cur).strip("\n")
        if body:
            if kind == "markdown":
                body = "\n".join(l[2:] if l.startswith("# ") else l.lstrip("#") for l in body.splitlines())
            out.append((kind, body))
    for line in text.splitlines():
        m = re.match(r"# %%(\s*\[markdown\])?\s*$", line)
        if m:
            flush(); cur = []; kind = "markdown" if m.group(1) else "code"
        else:
            cur.append(line)
    flush()
    return out

def build(src, dst, lib=None, consts=None):
    cs = []
    if consts:
        cs.append(("markdown", "## Configuracion de esta corrida"))
        cs.append(("code", "\n".join(f"{k} = {v!r}" for k, v in consts.items())))
    if lib:
        cs.append(("markdown", "## Funciones comunes (eclib)"))
        cs.append(("code", Path(lib).read_text(encoding="utf8")))
    cs += cells(Path(src).read_text(encoding="utf8"))
    nb = {"nbformat": 4, "nbformat_minor": 5, "metadata": {"kernelspec": {"name": "python3", "display_name": "Python 3", "language": "python"}},
          "cells": [{"cell_type": k, "metadata": {}, "source": b.splitlines(True),
                     **({"outputs": [], "execution_count": None} if k == "code" else {})} for k, b in cs]}
    for i, c in enumerate(nb["cells"]): c["id"] = f"c{i:03d}"
    Path(dst).write_text(json.dumps(nb, ensure_ascii=False, indent=1), encoding="utf8")

if __name__ == "__main__":
    a = sys.argv[1:]
    lib = a[a.index("--lib") + 1] if "--lib" in a else None
    consts = {}
    for i, x in enumerate(a):
        if x == "--set":
            k, v = a[i + 1].split("=", 1); consts[k] = json.loads(v)
    build(a[0], a[1], lib, consts)
