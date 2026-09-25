#!/usr/bin/env python3
"""Check archive snapshot and magnetic-latitude coverage for candidate holdout events.

Reads local Aurorasaurus/GFZ inputs and saved NASA iSWA monthly directory listings.
It does not download model grids or print report-level locations or identifiers.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import re
from collections import Counter, defaultdict
from html.parser import HTMLParser
from pathlib import Path

from audit_kp_event_coverage import (
    CONTEXT_BY_GAP,
    group_cores,
    load_kp,
    observation_label,
    parse_utc,
    storm_cores,
)

UTC = dt.timezone.utc
SNAPSHOT_INTERVAL = dt.timedelta(minutes=15)
SNAPSHOT_RE = re.compile(r"^(\d{8}_\d{4})UT")
DIRECTORY_RE = re.compile(r"e_data/(\d{4})/(\d{2})")


class Hrefs(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.values: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "a":
            href = dict(attrs).get("href")
            if href:
                self.values.append(href)


def load_axes(path: Path) -> tuple[set[float], set[float]]:
    mlt_values: set[float] = set()
    mlat_values: set[float] = set()
    numeric_rows = 0
    with path.open(encoding="utf-8", errors="replace") as source:
        for line in source:
            fields = line.split()
            if len(fields) != 3:
                continue
            try:
                mlt, mlat, _ = (float(field) for field in fields)
            except ValueError:
                continue
            mlt_values.add(mlt)
            mlat_values.add(mlat)
            numeric_rows += 1
    if numeric_rows == 0 or len(mlt_values) * len(mlat_values) != numeric_rows:
        raise ValueError("sample grid does not contain a complete rectangular three-column grid")
    return mlt_values, mlat_values


def load_archive_index(directory: Path) -> tuple[set[dt.datetime], set[tuple[int, int]]]:
    snapshots: set[dt.datetime] = set()
    months: set[tuple[int, int]] = set()
    for path in directory.glob("*.html"):
        source = path.read_text(encoding="utf-8", errors="replace")
        directory_match = DIRECTORY_RE.search(source)
        if directory_match:
            months.add((int(directory_match.group(1)), int(directory_match.group(2))))
        parser = Hrefs()
        parser.feed(source)
        for href in parser.values:
            match = SNAPSHOT_RE.match(href)
            if match:
                snapshots.add(dt.datetime.strptime(match.group(1), "%Y%m%d_%H%M").replace(tzinfo=UTC))
    if not snapshots:
        raise ValueError("no OVATION snapshots found in the provided HTML indexes")
    return snapshots, months


def round_snapshot(value: dt.datetime) -> dt.datetime:
    epoch = value.timestamp()
    rounded = round(epoch / SNAPSHOT_INTERVAL.total_seconds()) * SNAPSHOT_INTERVAL.total_seconds()
    return dt.datetime.fromtimestamp(rounded, UTC)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observations", required=True, type=Path)
    parser.add_argument("--kp-json", required=True, type=Path)
    parser.add_argument("--archive-indexes", required=True, type=Path)
    parser.add_argument("--grid-sample", required=True, type=Path)
    parser.add_argument("--candidate-cutoff", default="2025-01-01T00:00:00Z")
    args = parser.parse_args()
    for path in (args.observations, args.kp_json, args.archive_indexes, args.grid_sample):
        if not path.exists():
            parser.error("input does not exist: {}".format(path))

    snapshots, indexed_months = load_archive_index(args.archive_indexes)
    mlt_axis, mlat_axis = load_axes(args.grid_sample)
    cutoff = parse_utc(args.candidate_cutoff)
    context = dt.timedelta(hours=CONTEXT_BY_GAP[48])
    events = group_cores(storm_cores(load_kp(args.kp_json)), dt.timedelta(hours=48))
    test_windows = [(start - context, end + context) for start, end in events if end + context > cutoff]

    rows_by_label: dict[str, Counter[str]] = defaultdict(Counter)
    needed_by_label: dict[str, set[dt.datetime]] = defaultdict(set)
    missing_snapshots: set[dt.datetime] = set()
    required_months: set[tuple[int, int]] = set()
    total_rows = 0
    invalid_coordinates = 0
    with args.observations.open(newline="", encoding="utf-8-sig") as source:
        for row in csv.DictReader(source):
            try:
                start, end = parse_utc(row["time_start"]), parse_utc(row["time_end"])
                if end < start:
                    continue
                midpoint = start + (end - start) / 2
            except (KeyError, TypeError, ValueError, OverflowError):
                continue
            if not any(window_start <= midpoint < window_end for window_start, window_end in test_windows):
                continue
            total_rows += 1
            label = observation_label(row)
            counts = rows_by_label[label]
            counts["reports"] += 1
            if label == "excluded_missing_or_other_sky":
                continue
            try:
                mlat = float(row["mlat"])
                mlt = float(row["mlt"]) % 24.0
            except (KeyError, TypeError, ValueError, OverflowError):
                invalid_coordinates += 1
                counts["invalid_coordinates"] += 1
                continue
            if mlat < min(mlat_axis) or mlat > max(mlat_axis):
                counts["outside_mlat_axis"] += 1
                continue
            snapshot = round_snapshot(midpoint)
            needed_by_label[label].add(snapshot)
            required_months.add((snapshot.year, snapshot.month))
            counts["within_mlat_axis"] += 1
            if snapshot in snapshots:
                counts["archive_snapshot_available"] += 1
            else:
                counts["archive_snapshot_missing"] += 1
                missing_snapshots.add(snapshot)

    missing_indexes = required_months - indexed_months
    if missing_indexes:
        parser.error("missing monthly archive index(es): {}".format(sorted(missing_indexes)))

    print("candidate_cutoff={} test_windows={} reports={}".format(
        cutoff.isoformat(), len(test_windows), total_rows
    ))
    print("sample_grid_axes: MLT {}..{} ({} values), MLat {}..{} ({} values)".format(
        min(mlt_axis), max(mlt_axis), len(mlt_axis),
        min(mlat_axis), max(mlat_axis), len(mlat_axis),
    ))
    print("label | reports | within MLat axis | outside MLat axis | archive hit | archive miss | unique required snapshots")
    for label in ("seen", "clear_not_seen", "excluded_missing_or_other_sky"):
        counts = rows_by_label[label]
        print("{} | {} | {} | {} | {} | {} | {}".format(
            label, counts["reports"], counts["within_mlat_axis"], counts["outside_mlat_axis"],
            counts["archive_snapshot_available"], counts["archive_snapshot_missing"],
            len(needed_by_label[label]),
        ))
    all_needed = set().union(*needed_by_label.values()) if needed_by_label else set()
    print("unique_required_snapshots={} unique_missing_snapshots={} invalid_coordinate_reports={} indexed_months={}".format(
        len(all_needed), len(missing_snapshots), invalid_coordinates, len(indexed_months)
    ))
    print("Coverage only: file presence and one sample grid's axes are checked; archived grid values have not been downloaded or matched.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
