"""Ultra-fast adaptive One-Euro filtering for 3D hand and body pose landmarks.

Eliminates high-frequency sensor noise, trembling, and landmark jitter during static sign holds,
while dynamically adapting cutoff frequency during rapid gesture transitions to ensure zero lag.
"""

from typing import Any, List, Optional
import numpy as np

from signora.core.hands import LandmarkPoint


class OneEuroFilter3D:
    """Vectorized One-Euro filter for (N, 3) landmark arrays."""

    def __init__(
        self,
        n_points: int = 21,
        min_cutoff: float = 0.85,
        beta: float = 0.008,
        d_cutoff: float = 1.0,
    ):
        self.n_points = n_points
        self.min_cutoff = float(min_cutoff)
        self.beta = float(beta)
        self.d_cutoff = float(d_cutoff)
        self.x_prev: Optional[np.ndarray] = None
        self.dx_prev: np.ndarray = np.zeros((n_points, 3), dtype=np.float32)
        self.t_prev: Optional[float] = None

    def reset(self) -> None:
        """Reset internal filter state."""
        self.x_prev = None
        self.dx_prev.fill(0.0)
        self.t_prev = None

    @staticmethod
    def _alpha(rate: float, cutoff: np.ndarray) -> np.ndarray:
        tau = 1.0 / (2.0 * np.pi * cutoff)
        te = 1.0 / rate
        return 1.0 / (1.0 + tau / te)

    def filter(self, x: np.ndarray, timestamp: float) -> np.ndarray:
        """Filter an (N, 3) coordinate array at given monotonic timestamp."""
        x = np.asarray(x, dtype=np.float32)
        if (
            self.x_prev is None
            or self.t_prev is None
            or timestamp < self.t_prev
            or (timestamp - self.t_prev) > 0.25
            or self.dx_prev.shape != x.shape
        ):
            self.x_prev = x.copy()
            self.dx_prev = np.zeros_like(x)
            self.t_prev = timestamp
            return x

        dt = max(1e-4, timestamp - self.t_prev)
        rate = 1.0 / dt

        # Estimate landmark velocity derivative
        dx = (x - self.x_prev) * rate
        alpha_d = self._alpha(rate, np.full_like(dx, self.d_cutoff))
        dx_hat = alpha_d * dx + (1.0 - alpha_d) * self.dx_prev
        self.dx_prev = dx_hat

        # Adapt cutoff frequency dynamically based on velocity
        speed = np.abs(dx_hat)
        cutoff = self.min_cutoff + self.beta * speed
        alpha = self._alpha(rate, cutoff)

        x_hat = alpha * x + (1.0 - alpha) * self.x_prev
        self.x_prev = x_hat
        self.t_prev = timestamp
        return x_hat

    def filter_landmarks(self, landmarks: List[Any], timestamp: float) -> List[LandmarkPoint]:
        """Convenience method to filter a list of duck-typed landmark objects."""
        if not landmarks:
            return landmarks
        n = len(landmarks)
        raw = np.array(
            [[float(pt.x), float(pt.y), float(getattr(pt, "z", 0.0))] for pt in landmarks],
            dtype=np.float32,
        )
        filtered = self.filter(raw, timestamp)
        return [LandmarkPoint(filtered[i, 0], filtered[i, 1], filtered[i, 2]) for i in range(n)]
