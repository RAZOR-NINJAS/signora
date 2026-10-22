"""Unit tests verifying landmark tracking smoothness, One-Euro filtering, and gesture disambiguation."""

import time
import numpy as np
import pytest

from signora.core.filter import OneEuroFilter3D
from signora.core.hands import LandmarkPoint
from signora.isl.topology import RealAITopologyClassifier


def test_one_euro_filter_jitter_reduction():
    """Verify OneEuroFilter3D significantly reduces high-frequency coordinate noise."""
    flt = OneEuroFilter3D(n_points=21, min_cutoff=1.0, beta=0.008)
    
    # Ground truth: static hand position at (0.5, 0.5, 0.0)
    rng = np.random.RandomState(42)
    raw_coords = []
    filtered_coords = []
    
    t = 0.0
    for _ in range(100):
        t += 0.033
        # Add high-frequency Gaussian sensor jitter (std = 0.01)
        noise = rng.normal(0, 0.01, size=(21, 3)).astype(np.float32)
        sample = np.full((21, 3), 0.5, dtype=np.float32) + noise
        raw_coords.append(sample[8, 0])  # Index fingertip X
        
        filtered = flt.filter(sample, t)
        filtered_coords.append(filtered[8, 0])
        
    raw_var = np.var(raw_coords[20:])
    filtered_var = np.var(filtered_coords[20:])
    
    # Filtered variance must be at least 70% lower than raw jitter variance
    assert filtered_var < raw_var * 0.30, f"Filtered var {filtered_var} vs raw var {raw_var}"


def test_topology_peace_vs_ok_disambiguation():
    """Verify that a Peace / V sign is never misclassified as OK."""
    classifier = RealAITopologyClassifier()
    
    # Construct a clean PEACE sign:
    # Wrist at (0.5, 0.8)
    # Index extended upright (0.45, 0.3)
    # Middle extended upright (0.55, 0.3)
    # Ring folded (0.52, 0.65)
    # Pinky folded (0.58, 0.65)
    # Thumb resting across ring finger (0.48, 0.60)
    peace_hand = np.zeros((21, 3), dtype=np.float32)
    peace_hand[0] = [0.5, 0.8, 0.0]  # Wrist
    
    # MCPs
    peace_hand[5] = [0.46, 0.55, 0.0]  # Index MCP
    peace_hand[9] = [0.50, 0.53, 0.0]  # Middle MCP
    peace_hand[13] = [0.54, 0.55, 0.0]  # Ring MCP
    peace_hand[17] = [0.58, 0.57, 0.0]  # Pinky MCP
    
    # Index extended
    peace_hand[6] = [0.45, 0.45, 0.0]
    peace_hand[7] = [0.44, 0.38, 0.0]
    peace_hand[8] = [0.43, 0.30, 0.0]
    
    # Middle extended
    peace_hand[10] = [0.52, 0.44, 0.0]
    peace_hand[11] = [0.54, 0.37, 0.0]
    peace_hand[12] = [0.56, 0.30, 0.0]
    
    # Ring folded
    peace_hand[14] = [0.53, 0.60, 0.0]
    peace_hand[15] = [0.52, 0.64, 0.0]
    peace_hand[16] = [0.51, 0.65, 0.0]
    
    # Pinky folded
    peace_hand[18] = [0.57, 0.61, 0.0]
    peace_hand[19] = [0.56, 0.64, 0.0]
    peace_hand[20] = [0.55, 0.65, 0.0]
    
    # Thumb folded over fingers
    peace_hand[1] = [0.48, 0.75, 0.0]
    peace_hand[2] = [0.46, 0.70, 0.0]
    peace_hand[3] = [0.47, 0.65, 0.0]
    peace_hand[4] = [0.48, 0.60, 0.0]
    
    result = classifier.classify([peace_hand], ["Right"])
    assert result is not None
    label, conf = result
    assert label == "PEACE", f"Expected PEACE but got {label}"
    assert label != "OK"


def test_topology_ok_requires_curled_index_and_pinch():
    """Verify that OK requires thumb-index pinch and curled index."""
    classifier = RealAITopologyClassifier()
    
    # Construct an OK sign:
    # Thumb tip and Index tip touching
    # Middle, Ring, Pinky extended
    ok_hand = np.zeros((21, 3), dtype=np.float32)
    ok_hand[0] = [0.5, 0.8, 0.0]  # Wrist
    
    # Palm MCPs
    ok_hand[5] = [0.46, 0.55, 0.0]  # Index MCP
    ok_hand[9] = [0.50, 0.53, 0.0]  # Middle MCP
    ok_hand[13] = [0.54, 0.55, 0.0]  # Ring MCP
    ok_hand[17] = [0.58, 0.57, 0.0]  # Pinky MCP
    
    # Thumb & Index forming ring (pinch at (0.42, 0.48))
    ok_hand[1] = [0.46, 0.72, 0.0]
    ok_hand[2] = [0.43, 0.65, 0.0]
    ok_hand[3] = [0.42, 0.56, 0.0]
    ok_hand[4] = [0.43, 0.48, 0.0]  # Thumb tip
    
    ok_hand[6] = [0.44, 0.50, 0.0]
    ok_hand[7] = [0.43, 0.48, 0.0]
    ok_hand[8] = [0.43, 0.48, 0.0]  # Index tip touching thumb
    
    # Middle extended upright
    ok_hand[10] = [0.50, 0.44, 0.0]
    ok_hand[11] = [0.50, 0.36, 0.0]
    ok_hand[12] = [0.50, 0.28, 0.0]
    
    # Ring extended upright
    ok_hand[14] = [0.54, 0.45, 0.0]
    ok_hand[15] = [0.54, 0.37, 0.0]
    ok_hand[16] = [0.54, 0.30, 0.0]
    
    # Pinky extended upright
    ok_hand[18] = [0.58, 0.47, 0.0]
    ok_hand[19] = [0.59, 0.40, 0.0]
    ok_hand[20] = [0.60, 0.33, 0.0]
    
    result = classifier.classify([ok_hand], ["Right"])
    assert result is not None
    label, conf = result
    assert label == "OK", f"Expected OK but got {label}"


def _make_open_hand(center_x=0.5, center_y=0.6):
    """Generate an open flat hand facing forward."""
    h = np.zeros((21, 3), dtype=np.float32)
    h[0] = [center_x, center_y, 0.0]  # Wrist
    # MCPs
    h[5] = [center_x - 0.04, center_y - 0.15, 0.0]   # Index MCP
    h[9] = [center_x, center_y - 0.16, 0.0]          # Middle MCP
    h[13] = [center_x + 0.04, center_y - 0.15, 0.0]  # Ring MCP
    h[17] = [center_x + 0.07, center_y - 0.13, 0.0]  # Pinky MCP
    # Thumb extended sideways
    h[1] = [center_x - 0.05, center_y - 0.05, 0.0]
    h[2] = [center_x - 0.08, center_y - 0.08, 0.0]
    h[3] = [center_x - 0.11, center_y - 0.11, 0.0]
    h[4] = [center_x - 0.14, center_y - 0.13, 0.0]   # Thumb tip
    # Index extended
    h[6] = [center_x - 0.04, center_y - 0.22, 0.0]
    h[7] = [center_x - 0.04, center_y - 0.28, 0.0]
    h[8] = [center_x - 0.04, center_y - 0.34, 0.0]   # Index tip
    # Middle extended
    h[10] = [center_x, center_y - 0.23, 0.0]
    h[11] = [center_x, center_y - 0.30, 0.0]
    h[12] = [center_x, center_y - 0.37, 0.0]         # Middle tip
    # Ring extended
    h[14] = [center_x + 0.04, center_y - 0.22, 0.0]
    h[15] = [center_x + 0.04, center_y - 0.28, 0.0]
    h[16] = [center_x + 0.04, center_y - 0.34, 0.0]  # Ring tip
    # Pinky extended
    h[18] = [center_x + 0.07, center_y - 0.18, 0.0]
    h[19] = [center_x + 0.07, center_y - 0.23, 0.0]
    h[20] = [center_x + 0.07, center_y - 0.28, 0.0]  # Pinky tip
    return h


def _make_pointing_hand(target_pt, wrist_x=0.3, wrist_y=0.7):
    """Generate a hand pointing its index finger directly at target_pt."""
    h = np.zeros((21, 3), dtype=np.float32)
    h[0] = [wrist_x, wrist_y, 0.0]
    # MCPs
    h[5] = [wrist_x + 0.05, wrist_y - 0.10, 0.0]
    h[9] = [wrist_x + 0.08, wrist_y - 0.08, 0.0]
    h[13] = [wrist_x + 0.10, wrist_y - 0.06, 0.0]
    h[17] = [wrist_x + 0.12, wrist_y - 0.04, 0.0]
    # Index pointing straight at target
    tx, ty, tz = target_pt
    h[6] = [wrist_x + 0.08, wrist_y - 0.15, 0.0]
    h[7] = [tx * 0.7 + h[6, 0] * 0.3, ty * 0.7 + h[6, 1] * 0.3, 0.0]
    h[8] = [tx, ty, tz]  # Index tip exactly at target
    # Middle, Ring, Pinky folded
    for mcp, tip_idx in [(9, 12), (13, 16), (17, 20)]:
        h[mcp + 1] = [h[mcp, 0], h[mcp, 1] + 0.03, 0.0]
        h[mcp + 2] = [h[mcp, 0], h[mcp, 1] + 0.05, 0.0]
        h[tip_idx] = [h[mcp, 0], h[mcp, 1] + 0.06, 0.0]
    # Thumb folded
    h[1] = [wrist_x + 0.03, wrist_y - 0.05, 0.0]
    h[2] = [wrist_x + 0.05, wrist_y - 0.06, 0.0]
    h[3] = [wrist_x + 0.06, wrist_y - 0.05, 0.0]
    h[4] = [wrist_x + 0.07, wrist_y - 0.04, 0.0]
    return h


def test_topology_two_handed_isl_vowels():
    """Verify standard ISLRTC two-handed vowel fingerspelling (A, E, I, O, U)."""
    classifier = RealAITopologyClassifier()
    base_hand = _make_open_hand(0.6, 0.6)

    # 1. 'A': Index touches non-dominant Thumb tip (landmark 4)
    ptr_a = _make_pointing_hand(base_hand[4], wrist_x=0.2, wrist_y=0.7)
    res_a = classifier.classify([ptr_a, base_hand], ["Right", "Left"])
    assert res_a is not None and res_a[0] == "A", f"Expected 'A' but got {res_a}"

    # 2. 'E': Index touches non-dominant Index tip (landmark 8)
    ptr_e = _make_pointing_hand(base_hand[8], wrist_x=0.2, wrist_y=0.6)
    res_e = classifier.classify([ptr_e, base_hand], ["Right", "Left"])
    assert res_e is not None and res_e[0] == "E", f"Expected 'E' but got {res_e}"

    # 3. 'I': Index touches non-dominant Middle tip (landmark 12)
    ptr_i = _make_pointing_hand(base_hand[12], wrist_x=0.2, wrist_y=0.6)
    res_i = classifier.classify([ptr_i, base_hand], ["Right", "Left"])
    assert res_i is not None and res_i[0] == "I", f"Expected 'I' but got {res_i}"

    # 4. 'O': Index touches non-dominant Ring tip (landmark 16)
    ptr_o = _make_pointing_hand(base_hand[16], wrist_x=0.2, wrist_y=0.6)
    res_o = classifier.classify([ptr_o, base_hand], ["Right", "Left"])
    assert res_o is not None and res_o[0] == "O", f"Expected 'O' but got {res_o}"

    # 5. 'U': Index touches non-dominant Pinky tip (landmark 20)
    ptr_u = _make_pointing_hand(base_hand[20], wrist_x=0.2, wrist_y=0.6)
    res_u = classifier.classify([ptr_u, base_hand], ["Right", "Left"])
    assert res_u is not None and res_u[0] == "U", f"Expected 'U' but got {res_u}"


def test_topology_two_handed_isl_consonants():
    """Verify two-handed consonants like X (crossed index fingers) and G (stacked fists)."""
    classifier = RealAITopologyClassifier()

    # 1. 'X': Crossed index fingers
    h1 = np.zeros((21, 3), dtype=np.float32)
    h2 = np.zeros((21, 3), dtype=np.float32)
    h1[0] = [0.45, 0.7, 0.0]
    h2[0] = [0.55, 0.7, 0.0]
    # MCPs
    h1[5] = [0.47, 0.55, 0.0]
    h2[5] = [0.53, 0.55, 0.0]
    h1[9] = [0.49, 0.55, 0.0]
    h2[9] = [0.51, 0.55, 0.0]
    # Index fingers crossing at (0.50, 0.42)
    h1[6] = [0.48, 0.48, 0.0]
    h1[7] = [0.50, 0.42, 0.0]
    h1[8] = [0.53, 0.36, 0.0]

    h2[6] = [0.52, 0.48, 0.0]
    h2[7] = [0.50, 0.42, 0.0]
    h2[8] = [0.47, 0.36, 0.0]

    res_x = classifier.classify([h1, h2], ["Right", "Left"])
    assert res_x is not None and res_x[0] == "X", f"Expected 'X' but got {res_x}"

    # 2. 'G': Stacked closed fists
    fist1 = np.zeros((21, 3), dtype=np.float32)
    fist2 = np.zeros((21, 3), dtype=np.float32)
    # Upper fist centered at (0.5, 0.4)
    fist1[0] = [0.5, 0.45, 0.0]
    fist1[9] = [0.5, 0.38, 0.0]
    # Lower fist centered at (0.5, 0.6)
    fist2[0] = [0.5, 0.65, 0.0]
    fist2[9] = [0.5, 0.58, 0.0]

    res_g = classifier.classify([fist1, fist2], ["Right", "Left"])
    assert res_g is not None and res_g[0] == "G", f"Expected 'G' but got {res_g}"


def test_topology_single_handed_letters():
    """Verify single-handed letters like 'L', 'I', 'B', 'Y'."""
    classifier = RealAITopologyClassifier()

    # 1. Letter 'I': Pinky extended upright, other fingers curled
    hand_i = np.zeros((21, 3), dtype=np.float32)
    hand_i[0] = [0.5, 0.7, 0.0]
    hand_i[5] = [0.46, 0.55, 0.0]
    hand_i[9] = [0.50, 0.54, 0.0]
    hand_i[13] = [0.54, 0.55, 0.0]
    hand_i[17] = [0.58, 0.56, 0.0]
    # Pinky extended
    hand_i[18] = [0.58, 0.46, 0.0]
    hand_i[19] = [0.58, 0.38, 0.0]
    hand_i[20] = [0.58, 0.30, 0.0]
    # Fold other fingers
    for mcp, tip in [(5, 8), (9, 12), (13, 16)]:
        hand_i[mcp + 1] = [hand_i[mcp, 0], hand_i[mcp, 1] + 0.03, 0.0]
        hand_i[mcp + 2] = [hand_i[mcp, 0], hand_i[mcp, 1] + 0.05, 0.0]
        hand_i[tip] = [hand_i[mcp, 0], hand_i[mcp, 1] + 0.06, 0.0]
    # Thumb folded
    hand_i[1] = [0.48, 0.65, 0.0]
    hand_i[2] = [0.47, 0.60, 0.0]
    hand_i[3] = [0.48, 0.56, 0.0]
    hand_i[4] = [0.49, 0.55, 0.0]

    res_i = classifier.classify([hand_i], ["Right"])
    assert res_i is not None and res_i[0] == "I", f"Expected 'I' but got {res_i}"

    # 2. Letter 'L': Index upright, thumb sideways at 90 deg
    hand_l = np.zeros((21, 3), dtype=np.float32)
    hand_l[0] = [0.5, 0.7, 0.0]
    hand_l[5] = [0.48, 0.55, 0.0]
    hand_l[9] = [0.52, 0.54, 0.0]
    # Thumb extended sideways
    hand_l[1] = [0.45, 0.65, 0.0]
    hand_l[2] = [0.40, 0.64, 0.0]
    hand_l[3] = [0.35, 0.64, 0.0]
    hand_l[4] = [0.30, 0.64, 0.0]
    # Index extended upright
    hand_l[6] = [0.48, 0.45, 0.0]
    hand_l[7] = [0.48, 0.37, 0.0]
    hand_l[8] = [0.48, 0.30, 0.0]
    # Fold others
    for mcp, tip in [(9, 12), (13, 16), (17, 20)]:
        hand_l[mcp + 1] = [0.52, 0.57, 0.0]
        hand_l[mcp + 2] = [0.52, 0.59, 0.0]
        hand_l[tip] = [0.52, 0.60, 0.0]

    res_l = classifier.classify([hand_l], ["Right"])
    assert res_l is not None and res_l[0] == "L", f"Expected 'L' but got {res_l}"


def test_spatial_pipeline_context_manager_and_synthetic_pose():
    """Verify SpatialPipeline context manager lifecycle and synthetic pose generation in fake mode."""
    from signora.core.spatial import SpatialPipeline

    with SpatialPipeline(use_fake=True) as pipe:
        assert pipe.delegate == "CPU"
        assert pipe.started.wait(timeout=3.0)
        frame, hands, handedness, pose = pipe.read(timeout=3.0)
        assert frame is not None
        assert pose is not None
        assert len(pose) == 33
        # Shoulders and nose should be within valid normalized frame range [0.0, 1.0]
        assert 0.0 <= pose[0].x <= 1.0
        assert 0.0 <= pose[11].x <= 1.0


def test_topology_classifier_empty_and_partial_hands():
    """Verify RealAITopologyClassifier handles empty and partial hand inputs safely."""
    classifier = RealAITopologyClassifier()
    assert classifier.classify([], []) is None
    assert classifier.classify([None], []) is None
    # Hand with fewer than 21 landmarks
    assert classifier.classify([np.zeros((10, 3))], ["Right"]) is None


def test_topology_letter_r_crossed_fingers():
    """Verify Letter R is recognized when index and middle fingers are crossed."""
    classifier = RealAITopologyClassifier()

    hand_r = np.zeros((21, 3), dtype=np.float32)
    hand_r[0] = [0.5, 0.7, 0.0]  # Wrist

    # MCPs: Index MCP (0.46) is to the left of Middle MCP (0.52)
    hand_r[5] = [0.46, 0.55, 0.0]
    hand_r[9] = [0.52, 0.54, 0.0]
    hand_r[13] = [0.56, 0.55, 0.0]
    hand_r[17] = [0.60, 0.56, 0.0]

    # Tips: Crossed! Index Tip crosses over to the right of Middle Tip
    hand_r[6] = [0.48, 0.45, 0.0]
    hand_r[7] = [0.51, 0.38, 0.0]
    hand_r[8] = [0.53, 0.30, 0.0]  # Index tip

    hand_r[10] = [0.50, 0.44, 0.0]
    hand_r[11] = [0.48, 0.37, 0.0]
    hand_r[12] = [0.46, 0.30, 0.0]  # Middle tip

    # Fold ring, pinky, thumb
    for mcp, tip in [(13, 16), (17, 20)]:
        hand_r[mcp + 1] = [hand_r[mcp, 0], hand_r[mcp, 1] + 0.03, 0.0]
        hand_r[mcp + 2] = [hand_r[mcp, 0], hand_r[mcp, 1] + 0.05, 0.0]
        hand_r[tip] = [hand_r[mcp, 0], hand_r[mcp, 1] + 0.06, 0.0]
    hand_r[1] = [0.48, 0.65, 0.0]
    hand_r[2] = [0.47, 0.60, 0.0]
    hand_r[3] = [0.48, 0.56, 0.0]
    hand_r[4] = [0.49, 0.55, 0.0]

    res_r = classifier.classify([hand_r], ["Right"])
    assert res_r is not None and res_r[0] == "R", f"Expected 'R' but got {res_r}"


def test_one_euro_filter_dynamic_points_and_time_reversal():
    """Verify OneEuroFilter3D handles dynamic shapes (e.g. 33 pose points) and backwards timestamps."""
    flt = OneEuroFilter3D(n_points=21)

    # Feed 33 points (pose)
    pose_sample = np.full((33, 3), 0.5, dtype=np.float32)
    filtered_pose = flt.filter(pose_sample, 1.0)
    assert filtered_pose.shape == (33, 3)

    # Feed second frame
    filtered_pose2 = flt.filter(pose_sample + 0.01, 1.033)
    assert filtered_pose2.shape == (33, 3)

    # Feed backwards timestamp (e.g. out of order frame)
    filtered_reversed = flt.filter(pose_sample, 0.5)
    assert filtered_reversed.shape == (33, 3)
    assert np.all(np.isfinite(filtered_reversed))

    # Test filter_landmarks with 33 points
    pts = [LandmarkPoint(0.1 * i, 0.2 * i, 0.0) for i in range(33)]
    filtered_pts = flt.filter_landmarks(pts, 2.0)
    assert len(filtered_pts) == 33


