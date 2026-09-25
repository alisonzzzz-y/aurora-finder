#!/usr/bin/env python3
"""Local, aggregate-only diagnostic for matching Aurorasaurus reports to OVATION grids.

This script does not train a model, choose activity thresholds, or estimate viewing
probabilities. It reads raw inputs locally and prints aggregate counts only.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

UTC = dt.timezone.utc
Point = Tuple[float, float, float]


def parse_utc(value: str) -> dt.datetime:
    """Parse an ISO timestamp, treating an offset-free source value as UTC."""
    parsed = dt.datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def rounded_snapshot(value: dt.datetime, interval_minutes: int) -> dt.datetime:
    seconds = value.timestamp()
    rounded = round(seconds / (interval_minutes * 60)) * interval_minutes * 60
    return dt.datetime.fromtimestamp(rounded, UTC)


def grid_path(directory: Path, timestamp: dt.datetime) -> Optional[Path]:
    prefix = timestamp.strftime("%Y%m%d_%H%M") + "UT"
    candidates = sorted(directory.glob(prefix + "*.txt"))
    if not candidates:
        # Also accept the compact filename used by temporary audit copies.
        compact = directory / (timestamp.strftime("%Y%m%d_%H%M") + ".txt")
        if compact.is_file():
            return compact
        return None
    return candidates[0]


def read_grid(path: Path) -> Dict[Tuple[float, float], float]:
    """Read numeric MLT, magnetic-latitude, value rows; ignore non-grid footer lines."""
    points: Dict[Tuple[float, float], float] = {}
    with path.open(encoding="utf-8", errors="replace") as source:
        for line in source:
            fields = line.split()
            if len(fields) != 3:
                continue
            try:
                mlt, mlat, value = (float(field) for field in fields)
            except ValueError:
                continue
            if not all(math.isfinite(number) for number in (mlt, mlat, value)):
                continue
            points[(mlt, mlat)] = value
    if not points:
        raise ValueError("No numeric three-column grid rows found in {}".format(path))
    return points


def nearest_axis(values: Iterable[float], target: float, circular: bool = False) -> float:
    if circular:
        normalized = target % 24.0
        return min(values, key=lambda value: min(abs(value - normalized), 24.0 - abs(value - normalized)))
    return min(values, key=lambda value: abs(value - target))


def quartiles(values: Sequence[float]) -> Tuple[Optional[float], Optional[float]]:
    if len(values) < 2:
        return None, None
    quartile_values = statistics.quantiles(values, n=4, method="exclusive")
    return quartile_values[0], quartile_values[2]


def format_number(value: Optional[float]) -> str:
    return "n/a" if value is None else "{:.4g}".format(value)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observations", required=True, type=Path, help="Path to the cleaned Aurorasaurus CSV")
    parser.add_argument("--grids", required=True, type=Path, help="Directory containing downloaded OVATION e_data text grids")
    parser.add_argument("--dates", required=True, nargs="+", help="UTC dates selected for a descriptive audit, YYYY-MM-DD")
    parser.add_argument("--interval-minutes", type=int, default=15, help="Archive snapshot interval (default: 15)")
    args = parser.parse_args()
    if args.interval_minutes <= 0:
        parser.error("--interval-minutes must be positive")
    selected_dates = set(args.dates)
    try:
        for day in selected_dates:
            dt.date.fromisoformat(day)
    except ValueError as error:
        parser.error(str(error))
    if not args.observations.is_file():
        parser.error("observations CSV not found: {}".format(args.observations))
    if not args.grids.is_dir():
        parser.error("grid directory not found: {}".format(args.grids))

    totals: Counter = Counter()
    outside_grid: Counter = Counter()
    missing_snapshot: Counter = Counter()
    invalid_row = 0
    grid_cache: Dict[Path, Dict[Tuple[float, float], float]] = {}
    matched: Dict[Tuple[str, str], List[float]] = defaultdict(list)
    template_grid: Optional[Dict[Tuple[float, float], float]] = None
    for candidate in sorted(args.grids.glob("*.txt")):
        try:
            template_grid = read_grid(candidate)
            if len(template_grid) >= 1000:
                break
            template_grid = None
        except ValueError:
            continue
    if template_grid is None:
        parser.error("no readable OVATION grid snapshot found in {}".format(args.grids))
    template_mlat = {coordinate[1] for coordinate in template_grid}
    template_mlt = {coordinate[0] for coordinate in template_grid}

    with args.observations.open(newline="", encoding="utf-8-sig") as source:
        for row in csv.DictReader(source):
            try:
                start = parse_utc(row["time_start"])
                end = parse_utc(row["time_end"])
                mlat = float(row["mlat"])
                mlt = float(row["mlt"]) % 24.0
                if not all(math.isfinite(value) for value in (mlat, mlt)) or end < start:
                    raise ValueError("invalid time or coordinate")
            except (KeyError, TypeError, ValueError, OverflowError):
                invalid_row += 1
                continue

            midpoint = start + (end - start) / 2
            day = midpoint.date().isoformat()
            if day not in selected_dates:
                continue

            if row.get("see_aurora", "").strip().lower() == "true":
                label = "seen"
            elif row.get("see_aurora", "").strip().lower() == "false" and row.get("sky_id", "").strip().lower() == "clea":
                label = "not_seen_clear_sky"
            else:
                label = "not_used_unknown_sky"
            totals[(day, label)] += 1
            if label == "not_used_unknown_sky":
                continue

            if mlat < min(template_mlat) or mlat > max(template_mlat):
                outside_grid[(day, label)] += 1
                continue
            snapshot = rounded_snapshot(midpoint, args.interval_minutes)
            path = grid_path(args.grids, snapshot)
            if path is None:
                missing_snapshot[(day, label)] += 1
                continue
            if path not in grid_cache:
                grid_cache[path] = read_grid(path)
            grid = grid_cache[path]
            mlt_values = {coordinate[0] for coordinate in grid}
            mlat_values = {coordinate[1] for coordinate in grid}
            if mlt_values != template_mlt or mlat_values != template_mlat:
                raise ValueError("Grid axes differ in {}".format(path))
            nearest_mlt = nearest_axis(mlt_values, mlt, circular=True)
            nearest_mlat = nearest_axis(mlat_values, mlat)
            matched[(day, label)].append(grid[(nearest_mlt, nearest_mlat)])

    print("Aggregate-only diagnostic. Values are model-grid outputs, not probabilities or validated categories.")
    print("UTC midpoint date | label | reports | outside latitude grid | missing snapshot | matched | median | p25-p75 | zero %")
    for day in sorted(selected_dates):
        for label in ("seen", "not_seen_clear_sky", "not_used_unknown_sky"):
            key = (day, label)
            values = matched[key]
            q25, q75 = quartiles(values)
            zeros = 100.0 * sum(value == 0 for value in values) / len(values) if values else None
            print("{} | {} | {} | {} | {} | {} | {} | {}-{} | {}".format(
                day,
                label,
                totals[key],
                outside_grid[key],
                missing_snapshot[key],
                len(values),
                format_number(statistics.median(values) if values else None),
                format_number(q25),
                format_number(q75),
                "n/a" if zeros is None else "{:.1f}".format(zeros),
            ))
    print("Invalid rows skipped: {}. Unique grid files read: {}.".format(invalid_row, len(grid_cache)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
