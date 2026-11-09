#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Copyright (c) 2026 DFD
"""
Summarizes Fractal performance-log sessions (folders written by LattePerfLog) for a time window.

    adb pull /sdcard/Android/data/dev.dfd.fractal/files/perf perf
    python3 tools/perf/summarize.py perf/* --from 60 --to 120 [--label "RP5 SD 480+"]

Prints one Markdown table row per session: average fps, 1% low (fps at the 99th-percentile
frametime), average and worst frametime, frames over 50 ms, shaders and pipelines compiled
(whole session and inside the window), process CPU and the busiest threads.
"""
import argparse
import csv
import math
import os
import sys
from collections import defaultdict


def read_info(path):
    info = {}
    try:
        with open(os.path.join(path, "info.txt"), encoding="utf-8") as f:
            for line in f:
                if ":" in line:
                    k, v = line.split(":", 1)
                    info[k.strip()] = v.strip()
    except OSError:
        pass
    return info


def read_csv(path, name):
    try:
        with open(os.path.join(path, name), newline="", encoding="utf-8") as f:
            return list(csv.DictReader(f))
    except OSError:
        return []


def percentile(sorted_values, p):
    if not sorted_values:
        return 0.0
    i = min(len(sorted_values) - 1, max(0, math.ceil(p * len(sorted_values)) - 1))
    return sorted_values[i]


def summarize(path, t_from, t_to, top_threads):
    info = read_info(path)
    frames = [r for r in read_csv(path, "frames.csv") if t_from * 1000 <= float(r["t_ms"]) < t_to * 1000]
    fts = sorted(float(r["ft_ms"]) for r in frames)
    seconds = read_csv(path, "seconds.csv")
    window = [r for r in seconds if t_from < float(r["t_s"]) <= t_to]
    threads = [r for r in read_csv(path, "threads.csv") if t_from < float(r["t_s"]) <= t_to]

    duration = (float(frames[-1]["t_ms"]) - float(frames[0]["t_ms"])) / 1000 if len(frames) > 1 else 0
    avg_fps = (len(frames) - 1) / duration if duration > 0 else 0
    p99 = percentile(fts, 0.99)
    per_thread = defaultdict(float)
    for r in threads:
        per_thread[r["name"]] += float(r["cpu_pct"])
    n_seconds = max(1, len(window))
    busiest = sorted(per_thread.items(), key=lambda kv: -kv[1])[:top_threads]

    def total(rows, key):
        return sum(float(r[key]) for r in rows if r.get(key))

    return {
        "title": info.get("title", "?"),
        "device": info.get("device", "?"),
        "gpu": info.get("gpu", "?"),
        "driver": info.get("driver", "?"),
        "last_s": float(seconds[-1]["t_s"]) if seconds else 0,
        "fps": avg_fps,
        "low1": 1000 / p99 if p99 else 0,
        "ft_avg": sum(fts) / len(fts) if fts else 0,
        "ft_max": fts[-1] if fts else 0,
        "over50": sum(1 for v in fts if v > 50),
        "shaders_all": total(seconds, "shaders") - total(seconds, "shaders_cached"),
        "pipelines_all": total(seconds, "pipelines"),
        "shaders_win": total(window, "shaders") - total(window, "shaders_cached"),
        "pipelines_win": total(window, "pipelines"),
        "pipeline_sync_ms_win": total(window, "pipeline_sync_ms"),
        "cpu": total(window, "cpu_process_pct") / n_seconds,
        # GPU time of the command buffers (Vulkan timestamps); None for logs from before the column existed
        "gpu_busy": total(window, "gpu_busy_ms") / n_seconds / 10 if window and "gpu_busy_ms" in window[0] else None,
        "threads": ", ".join(f"{name} {pct / n_seconds:.0f}%" for name, pct in busiest),
        "clocks": read_clocks(path, t_from, t_to),
    }


def read_clocks(path, t_from, t_to):
    """GPU clock, its thermal cap, the big CPU cap and battery temperature, sampled by run_scene.sh next to the session.
    Times are from the tap on the game, so the window matches only roughly (the log starts when the game boots)."""
    parent = os.path.dirname(os.path.abspath(path))
    files = [f for f in os.listdir(parent) if f.startswith("clocks_") and f.endswith(".csv")]
    if not files:
        return "–"
    with open(os.path.join(parent, files[0]), newline="") as f:
        rows = [r for r in csv.DictReader(f) if r.get("t_s", "").isdigit() and t_from <= int(r["t_s"]) <= t_to]
    if not rows:
        return "–"
    avg = lambda k: sum(float(r[k]) for r in rows) / len(rows)
    return f"{avg('gpu_mhz'):.0f}/{avg('gpu_max_mhz'):.0f} MHz, CPU máx {avg('big_cpu_max_mhz'):.0f}, {max(float(r['battery_c']) for r in rows):.0f} °C"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("sessions", nargs="+", help="session folders (perf/<date>_<titleid>)")
    ap.add_argument("--from", dest="t_from", type=float, default=60, help="window start, seconds after the game started (default 60)")
    ap.add_argument("--to", dest="t_to", type=float, default=120, help="window end (default 120)")
    ap.add_argument("--label", default="", help="text for the first column (e.g. the device profile)")
    ap.add_argument("--threads", type=int, default=3, help="busiest threads to list (default 3)")
    ap.add_argument("--no-header", action="store_true")
    args = ap.parse_args()

    if not args.no_header:
        print(f"| Aparelho / perfil | Jogo | GPU / driver | FPS médio | 1% low | frametime médio / pior (ms) | quadros > 50 ms | shaders compilados / pipelines criados (sessão) | na janela | espera por pipeline (ms) | CPU do processo | GPU ocupada | threads mais ocupadas | clock GPU (atual/teto), teto CPU grande, bateria |")
        print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for path in args.sessions:
        if not os.path.isdir(path):
            continue
        s = summarize(path, args.t_from, args.t_to, args.threads)
        if s["last_s"] < args.t_to:
            print(f"<!-- {path}: session lasted {s['last_s']:.0f}s, shorter than the window -->", file=sys.stderr)
        label = args.label or s["device"]
        gpu = "–" if s["gpu_busy"] is None else f"{s['gpu_busy']:.0f}%"
        print(f"| {label} | {s['title']} | {s['gpu']} / {s['driver']} | {s['fps']:.1f} | {s['low1']:.1f} | "
              f"{s['ft_avg']:.1f} / {s['ft_max']:.0f} | {s['over50']} | {s['shaders_all']:.0f} / {s['pipelines_all']:.0f} | "
              f"{s['shaders_win']:.0f} / {s['pipelines_win']:.0f} | {s['pipeline_sync_ms_win']:.0f} | {s['cpu']:.0f}% | {gpu} | {s['threads']} | {s['clocks']} |")


if __name__ == "__main__":
    main()
