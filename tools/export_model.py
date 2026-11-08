"""Downloads the public YOLOv8 playing-card model and converts it to TensorFlow Lite for the phone.

Writes model-out/cards.tflite and model-out/labels.txt (one class name per line, in class order).
Runs in GitHub Actions; see .github/workflows/build.yml.
"""
import shutil
from pathlib import Path

from huggingface_hub import hf_hub_download
from ultralytics import YOLO

REPO = "mustafakemal0146/playing-cards-yolov8"
FILE = "playing_cards_model_0_playing-cards-colab.pt"
IMGSZ = 640

out_dir = Path("model-out")
out_dir.mkdir(exist_ok=True)

pt = hf_hub_download(REPO, FILE, local_dir="model-src")
model = YOLO(pt)
names = model.names  # {index: name}
print("Classes:", len(names), names)

exported = Path(model.export(format="tflite", imgsz=IMGSZ))
print("Exported:", exported)
saved = exported.parent
f16 = sorted(saved.glob("*float16.tflite"))
f32 = sorted(saved.glob("*float32.tflite"))
chosen = (f16 or f32 or [exported])[0]
print("Using:", chosen)
shutil.copy(chosen, out_dir / "cards.tflite")
(out_dir / "labels.txt").write_text("\n".join(names[i] for i in range(len(names))) + "\n", encoding="utf-8")
print("Done:", list(out_dir.iterdir()))
