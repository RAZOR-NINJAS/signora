"""DTW (Dynamic Time Warping) and k-Nearest Neighbors classifier for ASL words.

Uses pure NumPy vectorized cost matrices and fast dynamic programming for DTW.
Provides scikit-learn compatible classifier with confidence thresholding.

Confidence calibration
----------------------
Confidence is derived from *distances measured on the training set itself*, never
from a hardcoded constant. Two scales are computed at fit time:

  ``intra_scale_``
      Median distance from each sample to its nearest same-class sample. This is
      "as close as two performances of the same sign normally are".
  ``inter_scale_``
      Median distance from each sample to its nearest different-class sample.
      This is "as far as a wrong sign normally is".

A query is accepted when its winning class beats the runner-up class by a clear
relative margin AND its distance is inside the inter-class scale. Both tests are
scale-free, so the same thresholds work whether the dataset is tightly clustered
(synthetic templates) or widely spread (real webcam recordings).
"""

from pathlib import Path
from typing import Any, Dict, Optional, Tuple, Union

import numpy as np
from sklearn.base import BaseEstimator, ClassifierMixin

from .config import CONFIDENCE_THRESHOLD, KNN_NEIGHBORS, MAX_ACCEPT_DISTANCE_RATIO

_EPS = 1e-9


def dtw_cost_matrix(s1: np.ndarray, s2: np.ndarray) -> np.ndarray:
    """Compute pairwise frame Euclidean distances between two sequences.

    Args:
        s1: Array of shape (N, D).
        s2: Array of shape (M, D).

    Returns:
        np.ndarray of shape (N, M) containing Euclidean distances.
    """
    s1 = np.asarray(s1, dtype=np.float32)
    s2 = np.asarray(s2, dtype=np.float32)

    # Flatten if passed as 3D (T, num_landmarks, coords)
    if s1.ndim == 3:
        s1 = s1.reshape(s1.shape[0], -1)
    if s2.ndim == 3:
        s2 = s2.reshape(s2.shape[0], -1)

    diff = s1[:, None, :] - s2[None, :, :]
    return np.linalg.norm(diff, axis=-1)


def dtw_distances_to_set(
    query: np.ndarray,
    refs: np.ndarray,
    window: Optional[int] = None,
    chunk: int = 64,
) -> np.ndarray:
    """DTW distance from one query sequence to every reference sequence.

    This is the hot path of live recognition. It vectorizes the dynamic program
    across the whole reference set at once, so the Python-level loop runs
    ``len(query)`` times instead of ``len(query) * len(ref)`` times. Results are
    identical to calling :func:`dtw_distance` on each pair.

    Args:
        query: Array of shape (N, D).
        refs: Array of shape (K, M, D).
        window: Optional Sakoe-Chiba band half-width. If None, unrestricted.
        chunk: Number of references processed per batch, to bound peak memory.

    Returns:
        np.ndarray of shape (K,) with normalized DTW distances.
    """
    q = np.asarray(query, dtype=np.float32)
    if q.ndim == 3:
        q = q.reshape(q.shape[0], -1)
    r = np.asarray(refs, dtype=np.float32)
    if r.ndim == 2:
        r = r[None, ...]

    K = len(r)
    out = np.empty(K, dtype=np.float32)
    N = len(q)
    if N == 0 or K == 0:
        return np.full(K, np.inf, dtype=np.float32)

    M = r.shape[1]
    if M == 0:
        return np.full(K, np.inf, dtype=np.float32)

    w = max(window, abs(N - M)) if window is not None else max(N, M)

    for start in range(0, K, chunk):
        block = r[start : start + chunk]
        k = len(block)

        # Pairwise frame distances (k, N, M) via the expansion
        # ||a-b||^2 = ||a||^2 + ||b||^2 - 2 a.b, which avoids materializing a
        # (k, N, M, D) difference tensor.
        #
        # The expansion is evaluated in float64 because in float32 the
        # cancellation between the three terms leaves a residual of order
        # 1e-7 for identical frames, which surfaces as a spurious DTW distance of
        # ~3e-4. A sequence must have distance ~0 from itself, so the extra
        # precision is required, not cosmetic. Only this one matmul is float64;
        # the DP loop below stays float32 where it dominates the runtime.
        q64 = q.astype(np.float64, copy=False)
        b64 = block.astype(np.float64, copy=False)
        gram = np.matmul(q64[None, :, :], b64.transpose(0, 2, 1))
        sq_q = (q64 * q64).sum(axis=1)[None, :, None]
        sq_r = (b64 * b64).sum(axis=2)[:, None, :]
        cost = np.sqrt(np.maximum(sq_q + sq_r - 2.0 * gram, 0.0)).astype(np.float32)

        prev = np.full((k, M + 1), np.inf, dtype=np.float32)
        curr = np.full((k, M + 1), np.inf, dtype=np.float32)
        prev[:, 0] = 0.0
        scratch = np.empty(k, dtype=np.float32)

        # The recurrence D[i][j] = c[i][j] + min(D[i-1][j], D[i][j-1], D[i-1][j-1])
        # is inherently sequential in j. It cannot be turned into a prefix scan
        # here: the scan formulation relies on cumulative sums of the cost row and
        # loses ~1e-3 of precision, which is far larger than the near-zero
        # distance a sequence must have from itself. The loop below therefore
        # stays sequential but writes through preallocated buffers, and every
        # reference in the batch is advanced by each step, so the Python-level
        # iteration count is N rather than N*M.
        for i in range(1, N + 1):
            curr.fill(np.inf)
            j_start = max(1, i - w)
            j_end = min(M + 1, i + w + 1)
            cost_i = cost[:, i - 1, :]
            for j in range(j_start, j_end):
                np.minimum(prev[:, j], curr[:, j - 1], out=scratch)
                np.minimum(scratch, prev[:, j - 1], out=scratch)
                np.add(cost_i[:, j - 1], scratch, out=curr[:, j])
            prev, curr = curr, prev

        out[start : start + k] = prev[:, M] / (N + M)

    return out


def dtw_distance(
    s1: np.ndarray,
    s2: np.ndarray,
    window: Optional[int] = None,
) -> float:
    """Compute normalized Dynamic Time Warping distance between two sequences.

    Args:
        s1: Array of shape (N, D).
        s2: Array of shape (M, D).
        window: Optional Sakoe-Chiba band width. If None, full warping window is used.

    Returns:
        Normalized DTW distance float >= 0.0.
    """
    a = np.asarray(s1, dtype=np.float32)
    b = np.asarray(s2, dtype=np.float32)
    if a.ndim == 3:
        a = a.reshape(a.shape[0], -1)
    if b.ndim == 3:
        b = b.reshape(b.shape[0], -1)

    if len(a) == 0 or len(b) == 0:
        return float("inf")

    return float(dtw_distances_to_set(a, b[None, ...], window=window)[0])


def dtw_distance_matrix(
    queries: np.ndarray,
    targets: np.ndarray,
    window: Optional[int] = None,
) -> np.ndarray:
    """Compute pairwise DTW distances between a set of queries and targets.

    Args:
        queries: Array of shape (Q, T_q, D) or (T_q, D) for single query.
        targets: Array of shape (K, T_t, D).
        window: Optional Sakoe-Chiba band width.

    Returns:
        np.ndarray of shape (Q, K) or (K,) for single query.
    """
    is_single = (queries.ndim == 2) or (queries.ndim == 3 and queries.shape[0] == 1)
    if queries.ndim == 2:
        queries = queries[None, ...]

    Q = queries.shape[0]
    K = targets.shape[0]
    dist_mat = np.empty((Q, K), dtype=np.float32)

    for q in range(Q):
        dist_mat[q] = dtw_distances_to_set(queries[q], targets, window=window)

    if is_single:
        return dist_mat[0]
    return dist_mat


class DTWKNNClassifier(BaseEstimator, ClassifierMixin):
    """k-Nearest Neighbors classifier using Dynamic Time Warping distance.

    Features:
    - Pure NumPy DTW, vectorized across the reference set
    - Inverse-distance kNN voting, then class-level separation margin
    - Confidence calibrated from training-set distance scales (no magic constants)
    - Full scikit-learn estimator interface (fit, predict, predict_proba, score)
    - Persistence via save() and load()

    Why class-level separation instead of a fixed distance scale
    -----------------------------------------------------------
    A single hardcoded ``distance_scale`` cannot work here. The distance
    distribution depends entirely on the data: synthetic templates sit ~0.05
    apart, while genuine webcam performances of the same sign sit ~1.2-1.9
    apart. Any fixed scale is simultaneously far too tight for real input
    (every real query is rejected as low-confidence) and far too loose for
    synthetic input. Instead the winning class must merely be *clearly closer
    than the runner-up class*, which is scale-free and transfers across both
    regimes.
    """

    def __init__(
        self,
        n_neighbors: int = KNN_NEIGHBORS,
        confidence_threshold: float = CONFIDENCE_THRESHOLD,
        window: Optional[int] = None,
        distance_scale: Optional[float] = None,
        max_accept_distance_ratio: float = MAX_ACCEPT_DISTANCE_RATIO,
    ):
        self.n_neighbors = n_neighbors
        self.confidence_threshold = confidence_threshold
        self.window = window
        # Retained for backwards compatibility with previously saved models.
        # New code derives its scales from the training data instead.
        self.distance_scale = distance_scale
        # A query farther than this multiple of the inter-class scale from the
        # nearest reference is rejected outright as "not a known sign".
        self.max_accept_distance_ratio = max_accept_distance_ratio

        self.X_: Optional[np.ndarray] = None
        self.y_: Optional[np.ndarray] = None
        self.classes_: Optional[np.ndarray] = None
        self.intra_scale_: Optional[float] = None
        self.inter_scale_: Optional[float] = None

    def fit(self, X: np.ndarray, y: np.ndarray) -> "DTWKNNClassifier":
        """Fit the classifier and calibrate confidence on the training data.

        Args:
            X: Array of shape (N_samples, T_frames, D_features) or (N_samples, T, L, C).
            y: Array of shape (N_samples,) containing class labels.
        """
        X_arr = np.asarray(X, dtype=np.float32)
        if X_arr.ndim == 4:
            N, T, L, C = X_arr.shape
            X_arr = X_arr.reshape(N, T, L * C)
        elif X_arr.ndim != 3:
            raise ValueError(f"Expected 3D or 4D X array, got shape {X_arr.shape}")

        y_arr = np.asarray(y)
        if len(X_arr) != len(y_arr):
            raise ValueError(f"Length mismatch: len(X)={len(X_arr)}, len(y)={len(y_arr)}")
        if len(X_arr) == 0:
            raise ValueError("Cannot fit classifier with empty dataset.")

        self.X_ = X_arr
        self.y_ = y_arr
        self.classes_ = np.unique(y_arr)
        self._calibrate()
        return self

    def _calibrate(self) -> None:
        """Measure typical same-class and different-class distances in this dataset.

        Uses leave-one-out nearest-neighbour distances so a sample never
        calibrates against itself.
        """
        n = len(self.X_)
        if n < 2:
            self.intra_scale_ = 1.0
            self.inter_scale_ = 1.0
            return

        same = np.empty(n, dtype=np.float32)
        diff = np.empty(n, dtype=np.float32)
        labels = np.asarray(self.y_)

        for i in range(n):
            d = dtw_distances_to_set(self.X_[i], self.X_, window=self.window)
            d[i] = np.inf  # exclude self-match
            is_same = labels == labels[i]
            same[i] = d[is_same].min() if np.any(is_same & np.isfinite(d)) else np.nan
            diff[i] = d[~is_same].min() if np.any(~is_same) else np.nan

        # Guard the corner case of a single-class dataset.
        finite_diff = diff[np.isfinite(diff)]
        finite_same = same[np.isfinite(same)]

        self.inter_scale_ = float(np.median(finite_diff)) if finite_diff.size else 1.0
        self.intra_scale_ = float(np.median(finite_same)) if finite_same.size else 0.0

        if self.inter_scale_ <= _EPS:
            self.inter_scale_ = 1.0

    def _compute_query_distances(self, query: np.ndarray) -> np.ndarray:
        """Compute DTW distances from query to all stored samples."""
        if self.X_ is None or self.y_ is None:
            raise RuntimeError("Classifier has not been fitted yet.")

        q = np.asarray(query, dtype=np.float32)
        if q.ndim == 3:
            q = q.reshape(q.shape[0], -1)

        return dtw_distances_to_set(q, self.X_, window=self.window)

    def _class_distances(self, distances: np.ndarray) -> np.ndarray:
        """Reduce per-sample distances to one distance per class (minimum)."""
        assert self.classes_ is not None
        out = np.empty(len(self.classes_), dtype=np.float32)
        labels = np.asarray(self.y_)
        for idx, cls in enumerate(self.classes_):
            sel = distances[labels == cls]
            out[idx] = sel.min() if sel.size else np.inf
        return out

    def predict_single(
        self,
        query: np.ndarray,
        threshold: Optional[float] = None,
    ) -> Tuple[Optional[str], float, Dict[str, Any]]:
        """Predict class and confidence for a single sequence.

        Confidence combines two independent, scale-free signals:

        1. **vote_prob** - inverse-distance kNN vote share for the winning class.
        2. **separation** - relative gap between the winning class's nearest
           reference and the runner-up class's nearest reference.

        ``confidence = vote_prob * separation`` is high only when several
        neighbours agree *and* the winning class is clearly nearer than any
        other class. A query that is uniformly distant from everything (i.e. a
        sign the model has never seen) is rejected by the absolute distance
        gate rather than being assigned an arbitrary label.

        Args:
            query: Sequence of shape (T, D) or (T, L, C).
            threshold: Confidence threshold. If None, self.confidence_threshold is used.

        Returns:
            Tuple of:
            - predicted_label (str or None if below threshold or rejected)
            - confidence (float in [0, 1])
            - details (dict with voting probabilities, distances, and reject reason)
        """
        thresh = self.confidence_threshold if threshold is None else threshold
        empty_details = {
            "raw_label": None,
            "vote_prob": 0.0,
            "best_dist": float("inf"),
            "quality": 0.0,
            "separation": 0.0,
            "intra_scale": self.intra_scale_,
            "inter_scale": self.inter_scale_,
            "top_distances": [],
            "top_labels": [],
            "class_probs": {},
            "reject_reason": "empty query",
        }

        q_arr = np.asarray(query, dtype=np.float32)
        if q_arr.ndim == 3:
            q_arr = q_arr.reshape(q_arr.shape[0], -1)
        if len(q_arr) == 0:
            return None, 0.0, empty_details

        distances = self._compute_query_distances(query)

        if not np.any(np.isfinite(distances)):
            return None, 0.0, {**empty_details, "reject_reason": "no finite reference"}

        k = min(self.n_neighbors, len(distances))
        nn_indices = np.argsort(distances)[:k]
        top_distances = distances[nn_indices]
        top_labels = self.y_[nn_indices]

        # Inverse distance weights
        weights = 1.0 / (top_distances + 1e-4)
        total_weight = float(np.sum(weights))

        if total_weight <= 0.0 or not np.isfinite(total_weight):
            return None, 0.0, {**empty_details, "reject_reason": "zero weight"}

        # Class voting probabilities
        class_probs: Dict[str, float] = {}
        for idx, lbl in enumerate(top_labels):
            class_probs[lbl] = class_probs.get(lbl, 0.0) + float(weights[idx] / total_weight)

        # Winning class by nearest reference (not only by vote share)
        cls_dist = self._class_distances(distances)
        cls_order = np.argsort(cls_dist)
        best_idx = int(cls_order[0])
        best_label = self.classes_[best_idx]

        class_probs.setdefault(best_label, 0.0)
        vote_prob = class_probs[best_label]

        best_dist = float(cls_dist[best_idx])

        # --- Separation: relative gap to the runner-up class -------------
        separation = 0.0
        runner_up = float("inf")
        if len(cls_order) > 1:
            runner_up = float(cls_dist[cls_order[1]])
            if np.isfinite(runner_up) and runner_up > _EPS:
                separation = float(np.clip((runner_up - best_dist) / runner_up, 0.0, 1.0))

        confidence = float(vote_prob * separation)

        details = {
            "raw_label": best_label,
            "vote_prob": vote_prob,
            "best_dist": best_dist,
            "runner_up_dist": runner_up,
            "separation": separation,
            "intra_scale": self.intra_scale_,
            "inter_scale": self.inter_scale_,
            "top_distances": top_distances.tolist(),
            "top_labels": top_labels.tolist(),
            "class_probs": class_probs,
            "reject_reason": None,
        }

        # --- Absolute gate: reject input unlike anything in the dataset ---
        inter = self.inter_scale_ if self.inter_scale_ else 1.0
        limit = self.max_accept_distance_ratio * inter
        if best_dist > limit:
            details["reject_reason"] = (
                f"too far from all references ({best_dist:.3f} > {limit:.3f})"
            )
            return None, 0.0, details

        if confidence >= thresh:
            return best_label, confidence, details
        return None, confidence, {**details, "reject_reason": "low confidence"}

    predict_one = predict_single

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        """Compute class probability matrix for batch of sequences.

        Args:
            X: Array of shape (N_samples, T, D) or (N_samples, T, L, C).

        Returns:
            np.ndarray of shape (N_samples, n_classes) with class probabilities.
        """
        if self.classes_ is None:
            raise RuntimeError("Classifier has not been fitted yet.")

        X_arr = np.asarray(X, dtype=np.float32)
        if X_arr.ndim == 4:
            N, T, L, C = X_arr.shape
            X_arr = X_arr.reshape(N, T, L * C)

        n_samples = len(X_arr)
        n_classes = len(self.classes_)
        class_to_idx = {cls: idx for idx, cls in enumerate(self.classes_)}
        probs = np.zeros((n_samples, n_classes), dtype=np.float32)

        for i in range(n_samples):
            _, _, details = self.predict_single(X_arr[i], threshold=0.0)
            for cls, p in details["class_probs"].items():
                if cls in class_to_idx:
                    probs[i, class_to_idx[cls]] = p

        return probs

    def predict(self, X: np.ndarray) -> np.ndarray:
        """Predict class labels for batch of sequences.

        Labels with confidence below threshold are labeled as "UNKNOWN".
        """
        X_arr = np.asarray(X, dtype=np.float32)
        if X_arr.ndim == 4:
            N, T, L, C = X_arr.shape
            X_arr = X_arr.reshape(N, T, L * C)

        preds = []
        for i in range(len(X_arr)):
            lbl, _, _ = self.predict_single(X_arr[i])
            preds.append(lbl if lbl is not None else "UNKNOWN")
        return np.array(preds, dtype=object)

    def score(self, X: np.ndarray, y: np.ndarray) -> float:
        """Calculate accuracy on test set (ignoring UNKNOWN / threshold rejects as misses)."""
        preds = self.predict(X)
        y_arr = np.asarray(y)
        return float(np.mean(preds == y_arr))

    def save(self, filepath: Union[str, Path]) -> None:
        """Save fitted classifier model to an NPZ archive."""
        if self.X_ is None or self.y_ is None or self.classes_ is None:
            raise RuntimeError("Cannot save unfitted classifier.")

        path = Path(filepath)
        path.parent.mkdir(parents=True, exist_ok=True)

        np.savez_compressed(
            path,
            X=self.X_,
            y=self.y_,
            classes=self.classes_,
            n_neighbors=self.n_neighbors,
            confidence_threshold=self.confidence_threshold,
            distance_scale=-1.0 if self.distance_scale is None else self.distance_scale,
            window=self.window if self.window is not None else -1,
            intra_scale=-1.0 if self.intra_scale_ is None else self.intra_scale_,
            inter_scale=-1.0 if self.inter_scale_ is None else self.inter_scale_,
            max_accept_distance_ratio=self.max_accept_distance_ratio,
        )

    @classmethod
    def load(cls, filepath: Union[str, Path]) -> "DTWKNNClassifier":
        """Load fitted classifier from an NPZ archive.

        Models written before confidence calibration existed have no
        ``intra_scale``/``inter_scale`` fields; those are recalculated from the
        stored samples so an old model file still behaves correctly.
        """
        path = Path(filepath)
        if not path.exists():
            raise FileNotFoundError(f"Model file not found: {path}")

        data = np.load(path, allow_pickle=True)
        files = set(data.files)

        window_val = int(data["window"])
        window = window_val if window_val >= 0 else None

        ds = float(data["distance_scale"])
        clf = cls(
            n_neighbors=int(data["n_neighbors"]),
            confidence_threshold=float(data["confidence_threshold"]),
            window=window,
            distance_scale=ds if ds > 0 else None,
        )
        if "max_accept_distance_ratio" in files:
            clf.max_accept_distance_ratio = float(data["max_accept_distance_ratio"])

        clf.X_ = data["X"]
        clf.y_ = data["y"]
        clf.classes_ = data["classes"]

        if {"intra_scale", "inter_scale"} <= files:
            intra = float(data["intra_scale"])
            inter = float(data["inter_scale"])
            clf.intra_scale_ = None if intra < 0 else intra
            clf.inter_scale_ = None if inter < 0 else inter
        else:
            print("  [calibration] Model predates confidence calibration; recalibrating...")
            clf._calibrate()

        return clf
