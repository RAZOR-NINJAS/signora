"""PyTorch model and predictor for ASL fingerspelling."""

from pathlib import Path
from typing import Optional, Tuple

import numpy as np
import torch
import torch.nn as nn

from signora.asl.config import get_asl_weights_path


class SignMLP(nn.Module):
    """Multi-Layer Perceptron for 74D geometric static hand features."""

    def __init__(
        self,
        in_dim: Optional[int] = None,
        n_classes: Optional[int] = None,
        in_features: Optional[int] = None,
        num_classes: Optional[int] = None,
        hidden: Tuple[int, int] = (192, 112),
        dropout: float = 0.28,
    ):
        super().__init__()
        self.in_dim = (
            in_dim if in_dim is not None else (in_features if in_features is not None else 74)
        )
        self.n_classes = (
            n_classes if n_classes is not None else (num_classes if num_classes is not None else 27)
        )
        layers = []
        prev = self.in_dim
        for h in hidden:
            layers.extend(
                [
                    nn.Linear(prev, h),
                    nn.LayerNorm(h),
                    nn.GELU(),
                    nn.Dropout(dropout),
                ]
            )
            prev = h
        layers.append(nn.Linear(prev, self.n_classes))
        self.net = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


def load_asl_predictor(path: Optional[Path] = None) -> "Predictor":
    """Load model weights and return initialized Predictor."""
    target_path = path or get_asl_weights_path()
    if not target_path.exists():
        raise FileNotFoundError(
            f"No ASL letter model found at {target_path}.\n"
            "Run 'signora models fetch' to download required weights."
        )

    ckpt = torch.load(target_path, map_location="cpu", weights_only=False)
    model = SignMLP(ckpt["in_dim"], ckpt["n_classes"])
    model.load_state_dict(ckpt["state_dict"])
    model.eval()
    return Predictor(model=model, ckpt=ckpt)


class Predictor:
    """Predictor interface for ASL fingerspelling."""

    def __init__(
        self,
        model: Optional[SignMLP] = None,
        ckpt: Optional[dict] = None,
        path: Optional[Path] = None,
    ):
        if model is None or ckpt is None:
            loaded = load_asl_predictor(path)
            self.model = loaded.model
            self.labels = loaded.labels
            self.mean = loaded.mean
            self.std = loaded.std
            self.temperature = loaded.temperature
        else:
            self.model = model
            self.labels = list(ckpt["labels"])
            self.mean = np.asarray(ckpt["mean"], dtype=np.float32)
            self.std = np.asarray(ckpt["std"], dtype=np.float32)
            self.temperature = float(ckpt.get("temperature", 1.0) or 1.0)

    def probs(self, feats: np.ndarray) -> np.ndarray:
        """Compute softmax probability distribution over ASL classes."""
        safe_std = np.where(self.std > 1e-8, self.std, 1.0)
        norm_feats = (np.asarray(feats, dtype=np.float32) - self.mean) / safe_std
        with torch.no_grad():
            t = torch.from_numpy(norm_feats).unsqueeze(0)
            logits = self.model(t).squeeze(0) / self.temperature
            probs = torch.softmax(logits, dim=-1).numpy()
        return probs.astype(np.float32)

    def predict(self, feats: np.ndarray) -> Tuple[str, float]:
        """Predict top class and confidence score."""
        probs = self.probs(feats)
        idx = int(np.argmax(probs))
        return self.labels[idx], float(probs[idx])
