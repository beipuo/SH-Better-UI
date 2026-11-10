"""Summarize raw core and native kernel benchmark directories (Python 3.10+)."""
import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path


METRICS = {
    "raw.csv": ("elapsed_ns", "allocated_bytes"),
    "frames.csv": (
        "work_ns", "work_allocated_bytes", "frame_wall_ns", "frame_cpu_ns",
        "frame_allocated_bytes", "pass_wall_ns", "pass_allocated_bytes", "gpu_ns",
    ),
    "reload.csv": ("reload_wall_ns", "reload_main_thread_allocated_bytes", "kernel_applied_ns"),
}


def summary(values):
    available = sorted(value for value in values if value >= 0)
    if not available:
        return 0, "NA", "NA", "NA"
    return (
        len(available), f"{statistics.fmean(available):.2f}",
        available[math.ceil(len(available) * 0.95) - 1],
        available[math.ceil(len(available) * 0.99) - 1],
    )


def collect(directories):
    groups = defaultdict(list)
    for directory in directories:
        files = [directory / name for name in METRICS if (directory / name).is_file()]
        if not files:
            raise ValueError(f"no raw data in {directory}")
        for path in files:
            with path.open(newline="", encoding="utf-8") as samples:
                for row in csv.DictReader(samples):
                    for metric in METRICS[path.name]:
                        groups[(row["scenario"], metric)].append(int(row[metric]))
    return groups


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("directories", nargs="+", type=Path, help="raw directories for one platform and revision")
    args = parser.parse_args()
    groups = collect(args.directories)
    if not groups:
        parser.error("no samples in raw files")
    with args.output.open("w", newline="", encoding="utf-8") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("scenario", "metric", "samples", "available_samples", "mean", "p95", "p99"))
        for (scenario, metric), values in groups.items():
            writer.writerow((scenario, metric, len(values), *summary(values)))


if __name__ == "__main__":
    main()
