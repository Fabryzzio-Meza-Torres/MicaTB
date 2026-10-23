# %% [markdown]
# # Prueba aislada de exportacion (CPU): ONNX, TFLite FP16, TFLite INT8, cada una en subproceso con timeout
# %%
import subprocess, time, sys, shutil
from pathlib import Path
r = subprocess.run("pip install -q ultralytics", shell=True, capture_output=True, text=True); print("pip ultralytics rc", r.returncode, flush=True)
SRC = next(Path("/kaggle/input").glob("**/yolo_ds"))
DSR = Path("/kaggle/temp/yolo_ds"); shutil.copytree(SRC, DSR)
names = "\n".join(f"  {i}: {n}" for i, n in enumerate(["G-cocci", "G+cocci", "G-bacilli", "G+bacilli"]))
(DSR / "data_base.yaml").write_text(f"path: {DSR}\ntrain: images/train\nval: images/val\ntest: images/test\nnames:\n{names}\n")
from ultralytics import YOLO
YOLO("yolov8n.pt"); W2 = Path("/kaggle/working/yolov8n.pt"); assert W2.exists()
for tag, kw in (("onnx", "format='onnx'"), ("tflite_fp16", "format='tflite', half=True"), ("tflite_int8", "format='tflite', int8=True, data='/kaggle/temp/yolo_ds/data_base.yaml'")):
    code = f"from ultralytics import YOLO; print('EXPORT OK', YOLO('{W2}').export(imgsz=640, {kw}))"
    t = time.time()
    try:
        r = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, timeout=1200, cwd="/kaggle/working")
        print(f"[{tag}] rc={r.returncode} {time.time()-t:.0f}s\n", (r.stdout + r.stderr)[-2500:], flush=True)
    except subprocess.TimeoutExpired as e:
        print(f"[{tag}] TIMEOUT 1200s", ((e.stdout or b"") if isinstance(e.stdout, bytes) else (e.stdout or ""))[-1500:], flush=True)
print(subprocess.run("pip freeze | grep -i -E '^(ultralytics|tensorflow|tf|onnx|onnx2tf|onnxruntime|numpy|torch|ai-edge|sng4onnx|onnx_graphsurgeon)'", shell=True, capture_output=True, text=True).stdout)
print(subprocess.run("ls -la /kaggle/working /kaggle/working/*_saved_model 2>/dev/null | head -40", shell=True, capture_output=True, text=True).stdout)
