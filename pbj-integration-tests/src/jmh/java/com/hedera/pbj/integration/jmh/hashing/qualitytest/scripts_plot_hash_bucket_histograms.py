#!/usr/bin/env python3
"""
Reads per-algorithm per-bucket counts exported by NonCryptographicHashQualityStateKeyTest
and plots bucket-occupancy histograms suitable for comparing hash quality.

Input format (per algorithm):
  - <ALG>.meta.json         : metadata with fields {algorithm, numBuckets, numInputs, countsFile, countsDtype, endianness}
  - <ALG>_counts_i32_le.bin : little-endian int32 array of length numBuckets with counts per bucket

Usage:
  python scripts/plot_hash_bucket_histograms.py /path/to/hash_quality_results/run_YYYYMMDD_HHMMSSZ [--max-k 400] [--overlay] [--logy]

Outputs:
  - One PNG per algorithm: hist_<ALG>.png
  - If --overlay: a combined overlay PNG: hist_overlay.png
"""
import argparse
import glob
import json
import math
import os
from pathlib import Path
from typing import Dict, Any, List, Tuple

import matplotlib.pyplot as plt
import numpy as np


def load_algorithm(meta_path: Path) -> Tuple[Dict[str, Any], np.ndarray]:
    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)
    counts_file = meta_path.parent / meta["countsFile"]
    dtype = np.int32
    if str(meta.get("endianness", "little")).lower().startswith("little"):
        dtype = np.dtype("<i4")
    elif str(meta.get("endianness", "little")).lower().startswith("big"):
        dtype = np.dtype(">i4")
    counts = np.fromfile(counts_file, dtype=dtype)
    if counts.size != int(meta["numBuckets"]):
        raise ValueError(f"Counts size {counts.size} != numBuckets {meta['numBuckets']} for {meta_path}")
    return meta, counts


def poisson_expected_counts(max_k: int, lam: float, num_buckets: int) -> np.ndarray:
    """
    Compute expected number of buckets with exactly k items for k=0..max_k under Poisson(lam).
    Uses stable recurrence: P(k+1) = P(k) * lam / (k+1)
    """
    exp_counts = np.zeros(max_k + 1, dtype=np.float64)
    p = math.exp(-lam)  # P(0)
    exp_counts[0] = p * num_buckets
    for k in range(0, max_k):
        p = p * lam / (k + 1)
        exp_counts[k + 1] = p * num_buckets
    return exp_counts


def compute_hist(counts: np.ndarray, max_k: int = None) -> Tuple[np.ndarray, np.ndarray]:
    """
    Returns (k_values, num_buckets_with_k) for k in [0..max_k]
    """
    hist = np.bincount(counts.astype(np.int64))
    if max_k is None:
        max_k = len(hist) - 1
    else:
        max_k = min(max_k, len(hist) - 1)
    k = np.arange(0, max_k + 1, dtype=np.int64)
    y = hist[: (max_k + 1)]
    return k, y


def plot_per_algorithm(
    meta: Dict[str, Any],
    k: np.ndarray,
    y: np.ndarray,
    out_dir: Path,
    show_poisson: bool = True,
    logy: bool = False,
):
    alg = meta["algorithm"]
    num_buckets = int(meta["numBuckets"])
    num_inputs = int(meta["numInputs"])
    lam = num_inputs / num_buckets

    fig, ax = plt.subplots(figsize=(10, 6))
    ax.bar(k, y, width=1.0, color="#4e79a7", alpha=0.7, label=f"Observed ({alg})", edgecolor="none")

    if show_poisson:
        y_exp = poisson_expected_counts(k.max(), lam, num_buckets)
        ax.plot(k, y_exp, color="#e15759", linewidth=2.0, label=f"Poisson λ={lam:.2f}")

    ax.set_title(f"Bucket occupancy histogram — {alg}\n(numInputs={num_inputs:,}, numBuckets={num_buckets:,}, λ≈{lam:.2f})")
    ax.set_xlabel("Items per bucket (k)")
    ax.set_ylabel("Number of buckets with exactly k items")
    if logy:
        ax.set_yscale("log")
        ax.set_ylabel("Number of buckets (log scale)")
    ax.grid(True, which="both", axis="y", linestyle=":", alpha=0.5)
    ax.legend()
    fig.tight_layout()
    out_path = out_dir / f"hist_{sanitize_filename(alg)}.png"
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def plot_overlay(
    alg_results: List[Tuple[Dict[str, Any], np.ndarray, np.ndarray]],
    out_dir: Path,
    normalize: bool = True,
    logy: bool = False,
):
    """
    Overlays histograms as lines for quick comparison.
    If normalize=True, y is fraction of buckets instead of absolute count.
    """
    fig, ax = plt.subplots(figsize=(11, 7))
    for meta, k, y in alg_results:
        label = meta["algorithm"]
        if normalize:
            y_plot = y / y.sum()  # fraction of buckets
            ax.set_ylabel("Fraction of buckets with exactly k items")
        else:
            y_plot = y
            ax.set_ylabel("Number of buckets with exactly k items")
        ax.plot(k, y_plot, linewidth=1.8, label=label)
    ax.set_xlabel("Items per bucket (k)")
    if logy:
        ax.set_yscale("log")
    ax.set_title("Bucket occupancy histograms — overlay")
    ax.grid(True, which="both", axis="y", linestyle=":", alpha=0.5)
    ax.legend()
    fig.tight_layout()
    out_path = out_dir / "hist_overlay.png"
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def sanitize_filename(s: str) -> str:
    return "".join(c if c.isalnum() or c in "._-" else "_" for c in s)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir", type=str, help="Path to run directory (hash_quality_results/run_YYYYMMDD_HHMMSSZ)")
    parser.add_argument("--max-k", type=int, default=None, help="Maximum k to plot (default: auto up to max observed)")
    parser.add_argument("--overlay", action="store_true", help="Also produce a combined overlay plot")
    parser.add_argument("--logy", action="store_true", help="Use logarithmic y-axis")
    args = parser.parse_args()

    run_dir = Path(args.results_dir)
    if not run_dir.exists():
        raise SystemExit(f"Directory not found: {run_dir}")

    meta_files = sorted(glob.glob(str(run_dir / "*.meta.json")))
    if not meta_files:
        raise SystemExit(f"No *.meta.json files found in {run_dir}")

    # Create an output subdir for plots
    out_dir = run_dir / "plots"
    out_dir.mkdir(parents=True, exist_ok=True)

    overlay_data: List[Tuple[Dict[str, Any], np.ndarray, np.ndarray]] = []

    for meta_path_str in meta_files:
        meta_path = Path(meta_path_str)
        meta, counts = load_algorithm(meta_path)
        k, y = compute_hist(counts, max_k=args.max_k)
        plot_per_algorithm(meta, k, y, out_dir, show_poisson=True, logy=args.logy)
        overlay_data.append((meta, k, y))

    if args.overlay:
        # Align k-range across algorithms to the minimum common max_k
        min_max_k = min(int(k[-1]) for _, k, _ in overlay_data)
        aligned = []
        for meta, k, y in overlay_data:
            if int(k[-1]) > min_max_k:
                aligned.append((meta, k[: min_max_k + 1], y[: min_max_k + 1]))
            else:
                aligned.append((meta, k, y))
        plot_overlay(aligned, out_dir, normalize=True, logy=args.logy)

    print(f"Done. Plots written to: {out_dir}")


if __name__ == "__main__":
    main()