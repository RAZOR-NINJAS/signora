"""ISL Multi-Layer Perceptron (PyTorch training + pure NumPy live inference)."""

from pathlib import Path
from typing import Any, List, Optional, Tuple

import numpy as np

from signora.isl.config import get_isl_weights_path


def _gelu_np(x: np.ndarray) -> np.ndarray:
    """Accurate numerical approximation of GELU activation in NumPy."""
    return 0.5 * x * (1.0 + np.tanh(np.sqrt(2.0 / np.pi) * (x + 0.044715 * (x**3))))


def _layer_norm_np(
    x: np.ndarray, weight: np.ndarray, bias: np.ndarray, eps: float = 1e-5
) -> np.ndarray:
    """NumPy implementation of LayerNorm."""
    mean = np.mean(x, axis=-1, keepdims=True)
    var = np.var(x, axis=-1, keepdims=True)
    norm = (x - mean) / np.sqrt(var + eps)
    return norm * weight + bias


class ISL_MLP:
    """NumPy-native inference engine for ISL MLP.

    Executes forward passes without importing PyTorch at runtime, preserving RAM
    and reducing thermal load on memory-constrained systems.
    """

    def __init__(
        self,
        weights: List[Tuple[np.ndarray, np.ndarray]],
        ln_params: List[Tuple[np.ndarray, np.ndarray]],
        labels: List[str],
        mean: np.ndarray,
        std: np.ndarray,
        temperature: float = 1.0,
    ):
        self.weights = weights
        self.ln_params = ln_params
        self.labels = list(labels)
        self.mean = np.asarray(mean, dtype=np.float32)
        self.std = np.asarray(std, dtype=np.float32)
        self.temperature = max(float(temperature), 0.01)

    def forward(self, feats: np.ndarray) -> np.ndarray:
        """Run forward inference and return probability distribution over classes."""
        feats_arr = np.asarray(feats, dtype=np.float32)
        in_dim = self.mean.shape[-1]
        if feats_arr.shape[-1] != in_dim:
            if feats_arr.ndim == 1:
                aligned = np.zeros(in_dim, dtype=np.float32)
                n = min(len(feats_arr), in_dim)
                aligned[:n] = feats_arr[:n]
                feats_arr = aligned
            elif feats_arr.ndim == 2:
                aligned = np.zeros((feats_arr.shape[0], in_dim), dtype=np.float32)
                n = min(feats_arr.shape[1], in_dim)
                aligned[:, :n] = feats_arr[:, :n]
                feats_arr = aligned

        if len(self.labels) == 0 or self.weights[2][0].shape[1] == 0:
            return np.zeros((feats_arr.shape[0], 0) if feats_arr.ndim == 2 else (0,), dtype=np.float32)

        safe_std = np.where(self.std > 1e-8, self.std, 1.0)
        x = (feats_arr - self.mean) / safe_std
        # Layer 1
        h = np.dot(x, self.weights[0][0]) + self.weights[0][1]
        h = _layer_norm_np(h, self.ln_params[0][0], self.ln_params[0][1])
        h = _gelu_np(h)

        # Layer 2
        h = np.dot(h, self.weights[1][0]) + self.weights[1][1]
        h = _layer_norm_np(h, self.ln_params[1][0], self.ln_params[1][1])
        h = _gelu_np(h)

        # Output projection
        logits = np.dot(h, self.weights[2][0]) + self.weights[2][1]
        logits = logits / self.temperature

        # Stable Softmax
        shift = logits - np.max(logits, axis=-1, keepdims=True)
        exp_logits = np.exp(shift)
        probs = exp_logits / np.sum(exp_logits, axis=-1, keepdims=True)
        return probs.astype(np.float32)

    def predict_proba(self, feats: np.ndarray) -> np.ndarray:
        return self.forward(feats)


def get_torch_model(
    in_dim: int, n_classes: int, hidden: Tuple[int, int] = (192, 112), dropout: float = 0.28
):
    """Factory creating PyTorch module for training (only imported during training)."""
    import torch.nn as nn

    class PyTorchISLMLP(nn.Module):
        def __init__(self):
            super().__init__()
            self.net = nn.Sequential(
                nn.Linear(in_dim, hidden[0]),
                nn.LayerNorm(hidden[0]),
                nn.GELU(),
                nn.Dropout(dropout),
                nn.Linear(hidden[0], hidden[1]),
                nn.LayerNorm(hidden[1]),
                nn.GELU(),
                nn.Dropout(dropout),
                nn.Linear(hidden[1], n_classes),
            )

        def forward(self, x):
            return self.net(x)

    return PyTorchISLMLP()


def load_numpy_predictor(path: Optional[Path] = None) -> ISL_MLP:
    """Load model weights directly into NumPy inference engine."""
    import torch

    target_path = path or get_isl_weights_path()
    if not target_path.exists():
        raise FileNotFoundError(
            f"No trained ISL model found at {target_path}.\n"
            "Run 'signora models fetch' or 'signora isl train' to train or download weights."
        )

    ckpt = torch.load(target_path, map_location="cpu", weights_only=False)
    state = ckpt["state_dict"]

    weights = [
        (state["net.0.weight"].numpy().T, state["net.0.bias"].numpy()),
        (state["net.4.weight"].numpy().T, state["net.4.bias"].numpy()),
        (state["net.8.weight"].numpy().T, state["net.8.bias"].numpy()),
    ]
    ln_params = [
        (state["net.1.weight"].numpy(), state["net.1.bias"].numpy()),
        (state["net.5.weight"].numpy(), state["net.5.bias"].numpy()),
    ]

    return ISL_MLP(
        weights=weights,
        ln_params=ln_params,
        labels=ckpt["labels"],
        mean=ckpt["mean"],
        std=ckpt["std"],
        temperature=float(ckpt.get("temperature", 1.0) or 1.0),
    )


class Predictor:
    """Public predictor interface for ISL recognition.

    Combines:
    1. Real AI Topological Gesture Engine: invariant 3D geometric matching for all ISL signs and alphabet.
    2. Real AI Multimodal Vision (Google Gemini 2.0 / 1.5 Flash) when configured.
    3. Low-level Neural MLP weights fallback.
    """

    def __init__(self, path: Optional[Path] = None, enable_topology: bool = True):
        from signora.ai.gemini import GeminiAIEngine
        from signora.isl.topology import RealAITopologyClassifier

        self._enable_topology = enable_topology
        self._topology = RealAITopologyClassifier() if enable_topology else None
        self._gemini = GeminiAIEngine()
        self._engine: Optional[ISL_MLP] = None
        self._letters_engine: Optional[ISL_MLP] = None

        try:
            self._engine = load_numpy_predictor(path)
            self.labels = self._engine.labels
        except Exception:
            if not enable_topology:
                self.labels = []
            else:
                from signora.isl.config import STATIC_LETTERS, STATIC_WORDS
                self.labels = list(STATIC_LETTERS) + list(STATIC_WORDS)

        # Optional letters engine for single-handed fingerspelling fallback (only when topology is active)
        if enable_topology:
            from signora.models.manifest import get_default_models_dir
            letters_ckpt = get_default_models_dir() / "signs_mlp.pt"
            if letters_ckpt.exists():
                try:
                    self._letters_engine = load_numpy_predictor(letters_ckpt)
                except Exception:
                    self._letters_engine = None

    @property
    def has_engine(self) -> bool:
        return self._engine is not None

    @property
    def gemini_ai(self):
        return self._gemini

    def predict_from_landmarks(
        self,
        landmarks_list: List[Any],
        handedness_list: List[str],
        body_anchor: str = "OPEN_SPACE",
    ) -> Tuple[Optional[str], float]:
        """Classify gesture using real AI invariant 3D topology and letters fallback."""
        if self._enable_topology and self._topology is not None:
            res = self._topology.classify(landmarks_list, handedness_list, body_anchor)
            if res is not None:
                return res

        # Single-hand fingerspelling fallback via neural letters classifier (only in topology mode)
        if self._enable_topology and len(landmarks_list) == 1 and self._letters_engine is not None:
            try:
                from signora.core.features import landmark_features, normalize_hand
                hnd = handedness_list[0] if handedness_list else "Right"
                norm_hand = normalize_hand(landmarks_list[0], hnd)
                feats = landmark_features(norm_hand)
                probs = self._letters_engine.forward(feats)
                top_idx = int(np.argmax(probs))
                top_prob = float(probs[top_idx])
                if top_prob >= 0.55:
                    return self._letters_engine.labels[top_idx], top_prob
            except Exception:
                pass

        return None, 0.0

    def probs(self, feats: np.ndarray) -> np.ndarray:
        if self._engine:
            return self._engine.forward(feats)
        if not self.labels:
            return np.array([], dtype=np.float32)
        # Uniform fallback
        p = np.full(len(self.labels), 1.0 / len(self.labels), dtype=np.float32)
        return p

    def predict(self, feats: np.ndarray) -> Tuple[Optional[str], float]:
        if not self.labels:
            return None, 0.0
        probs = self.probs(feats)
        if len(probs) == 0:
            return None, 0.0
        idx = int(np.argmax(probs))
        return self.labels[idx], float(probs[idx])


FastNumPyMLP = ISL_MLP


def load_isl_predictor(path: Optional[Path] = None) -> Predictor:
    return Predictor(path)
