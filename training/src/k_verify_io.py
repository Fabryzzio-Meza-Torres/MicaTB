# %% [markdown]
# # Verificar formato de salida del TFLite FP16: escala de cajas, scores y decodificacion manual vs ultralytics
# %%
import subprocess, numpy as np, cv2, json
from pathlib import Path
subprocess.run("pip install -q ultralytics", shell=True)
from ultralytics import YOLO
import tensorflow as tf
F = next(Path("/kaggle/input").glob("**/ecoli_gb_E3s43_fp16.tflite")); DS = next(Path("/kaggle/input").glob("**/yolo_ds"))
img = sorted((DS / "images" / "test").glob("*.jpg"))[3]; im = cv2.imread(str(img)); print(img.name, im.shape)
# letterbox manual a 320 (imagen cuadrada 640 -> resize directo)
rgb = cv2.cvtColor(cv2.resize(im, (320, 320), interpolation=cv2.INTER_LINEAR), cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
x = np.transpose(rgb, (2, 0, 1))[None]
it = tf.lite.Interpreter(model_path=str(F)); it.allocate_tensors(); i0, o0 = it.get_input_details()[0], it.get_output_details()[0]
it.set_tensor(i0["index"], x); it.invoke(); y = it.get_tensor(o0["index"])[0]   # 8 x 2100
print("salida", y.shape, "filas 0-3 min/max:", [(round(float(y[k].min()), 3), round(float(y[k].max()), 3)) for k in range(4)], "scores min/max:", float(y[4:].min()), float(y[4:].max()))
sc = y[4:].max(0); cl = y[4:].argmax(0); keep = np.where(sc >= 0.6)[0]
print("candidatos conf>=0.6 sin NMS:", len(keep), [(int(cl[k]), round(float(sc[k]), 3), [round(float(v), 3) for v in y[:4, k]]) for k in keep[:6]])
r = YOLO(str(F), task="detect").predict(str(img), imgsz=320, conf=0.6, iou=0.5, device="cpu", verbose=False, max_det=1000)[0]
print("ultralytics:", [(int(c), round(float(s), 3), [round(float(v), 1) for v in b]) for c, s, b in zip(r.boxes.cls, r.boxes.conf, r.boxes.xyxy)][:6], "orig_shape", r.orig_shape)
