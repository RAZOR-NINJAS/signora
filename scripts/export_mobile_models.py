"""Export Signora PyTorch neural models and MediaPipe assets for Native Android."""

import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
if str(ROOT_DIR / "src") not in sys.path:
    sys.path.insert(0, str(ROOT_DIR / "src"))
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from signora.models.export import copy_mediapipe_landmarker, export_model_to_json


def main():
    root = Path(__file__).resolve().parent.parent
    assets_dir = root / "android" / "app" / "src" / "main" / "assets" / "models"
    assets_dir.mkdir(parents=True, exist_ok=True)

    print("=" * 60)
    print("  SIGNORA -- Exporting Models for Native Android App")
    print("=" * 60)

    export_model_to_json("asl_letters", "asl", assets_dir / "asl_model.json")
    spatial_ckpt = root / "models" / "isl_spatial_mlp.pt"
    if spatial_ckpt.exists():
        export_model_to_json(spatial_ckpt, "isl", assets_dir / "isl_model.json")
    else:
        export_model_to_json("isl_signs", "isl", assets_dir / "isl_model.json")
    copy_mediapipe_landmarker(assets_dir / "hand_landmarker.task")

    print(
        "\nAll mobile neural assets successfully generated in android/app/src/main/assets/models/"
    )


if __name__ == "__main__":
    main()
