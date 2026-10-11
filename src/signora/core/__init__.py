"""Core computer vision, feature extraction, and state machine primitives."""

from .camera import Camera, CameraError
from .debounce import DebounceEngine
from .fake_camera import FakeCamera
from .features import FEATURE_DIM, LANDMARK_DIM, extract_features, get_feature_dim
from .hands import HandPipeline, draw_landmarks

__all__ = [
    "Camera",
    "CameraError",
    "DebounceEngine",
    "FakeCamera",
    "HandPipeline",
    "draw_landmarks",
    "extract_features",
    "get_feature_dim",
    "FEATURE_DIM",
    "LANDMARK_DIM",
]
