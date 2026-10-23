# %% [markdown]
# # FP16 TFLite via ai-edge-quantizer sobre el FP32 exportado por ultralytics (CPU)
# %%
import subprocess, sys, inspect
from pathlib import Path
subprocess.run("pip install -q ultralytics", shell=True)
from ultralytics import YOLO
YOLO("yolov8n.pt")
r = subprocess.run([sys.executable, "-c", "from ultralytics import YOLO; print(YOLO('/kaggle/working/yolov8n.pt').export(imgsz=640, format='tflite'))"], capture_output=True, text=True, timeout=1500, cwd="/kaggle/working")
print("fp32 rc", r.returncode, r.stdout[-200:], flush=True)
# %%
from ai_edge_quantizer import quantizer, recipe
print([n for n in dir(recipe) if not n.startswith("_")])
for n in [n for n in dir(recipe) if "fp16" in n.lower() or "float16" in n.lower()]:
    print(n, inspect.getsource(getattr(recipe, n))[:600])
# %%
from ai_edge_quantizer import qtyping
q = quantizer.Quantizer("/kaggle/working/yolov8n.tflite")
cfg = qtyping.OpQuantizationConfig(weight_tensor_config=qtyping.TensorQuantizationConfig(num_bits=16, dtype=qtyping.TensorDataType.FLOAT), compute_precision=qtyping.ComputePrecision.FLOAT, explicit_dequantize=True)
q.update_quantization_recipe(regex=".*", operation_name=qtyping.TFLOperationName.ALL_SUPPORTED, algorithm_key="float_casting", op_config=cfg)
res = q.quantize(); res.export_model("/kaggle/working/yolov8n_fp16.tflite", overwrite=True)
print("FP16 OK", Path("/kaggle/working/yolov8n_fp16.tflite").stat().st_size, "vs fp32", Path("/kaggle/working/yolov8n.tflite").stat().st_size)
# %%
import numpy as np, tensorflow as tf
for f in ("yolov8n.tflite", "yolov8n_fp16.tflite"):
    it = tf.lite.Interpreter(model_path=f"/kaggle/working/{f}"); it.allocate_tensors()
    print(f, [(d["shape"].tolist(), str(d["dtype"].__name__)) for d in it.get_input_details()], [(d["shape"].tolist(), d["dtype"].__name__) for d in it.get_output_details()])
    x = np.random.rand(*it.get_input_details()[0]["shape"]).astype(np.float32); it.set_tensor(it.get_input_details()[0]["index"], x); it.invoke(); print("invoke ok")
