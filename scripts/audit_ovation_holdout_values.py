#!/usr/bin/env python3
"""Download and summarize OVATION values matched to candidate Kp holdout reports.

Inputs and grids stay local. Output contains aggregate counts and distributions only;
this is exploratory and does not estimate viewing probabilities or validate levels.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import math
import re
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from html.parser import HTMLParser
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

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
BASE_URL = "https://iswa.gsfc.nasa.gov/iswa_data_tree/model/ionosphere/ovation_prime/e_data/"
Point = Tuple[float, float]


class Hrefs(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.values: List[str] = []

    def handle_starttag(self, tag: str, attrs: List[Tuple[str, Optional[str]]]) -> None:
        if tag == "a":
            href = dict(attrs).get("href")
            if href:
                self.values.append(href)


def snapshot_time(filename: str) -> Optional[dt.datetime]:
    match = SNAPSHOT_RE.match(filename)
    if not match:
        return None
    return dt.datetime.strptime(match.group(1), "%Y%m%d_%H%M").replace(tzinfo=UTC)


def rounded_snapshot(value: dt.datetime) -> dt.datetime:
    interval = SNAPSHOT_INTERVAL.total_seconds()
    rounded = round(value.timestamp() / interval) * interval
    return dt.datetime.fromtimestamp(rounded, UTC)


def nearest_axis(values: Iterable[float], target: float, circular: bool = False) -> float:
    if circular:
        target %= 24.0
        return min(values, key=lambda value: min(abs(value - target), 24.0 - abs(value - target)))
    return min(values, key=lambda value: abs(value - target))


def load_indexes(directory: Path) -> Dict[dt.datetime, str]:
    snapshots: Dict[dt.datetime, str] = {}
    for path in directory.glob("*.html"):
        source = path.read_text(encoding="utf-8", errors="replace")
        directory_match = DIRECTORY_RE.search(source)
        if not directory_match:
            continue
        year, month = directory_match.groups()
        parser = Hrefs()
        parser.feed(source)
        for href in parser.values:
            filename = Path(urllib.parse.urlparse(href).path).name
            timestamp = snapshot_time(filename)
            if timestamp:
                snapshots[timestamp] = "{}/{}/{}".format(year, month, filename)
    if not snapshots:
        raise ValueError("no OVATION snapshots found in monthly index files")
    return snapshots


def download_one(item: Tuple[dt.datetime, str, Path]) -> Tuple[dt.datetime, Optional[Path], Optional[str]]:
    timestamp, relative_path, directory = item
    destination = directory / Path(relative_path).name
    if destination.is_file() and destination.stat().st_size > 0:
        return timestamp, destination, None
    url = urllib.parse.urljoin(BASE_URL, relative_path)
    request = urllib.request.Request(url, headers={"User-Agent": "AuroraFinder validation script (github.com/alisonzzzz-y/aurora-finder)"})
    last_error: Optional[Exception] = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                if response.status != 200:
                    return timestamp, None, "HTTP {}".format(response.status)
                payload = response.read()
            temporary = destination.with_suffix(destination.suffix + ".part")
            temporary.write_bytes(payload)
            temporary.replace(destination)
            return timestamp, destination, None
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = error
            if attempt < 3:
                time.sleep(0.5 * (2 ** attempt))
    return timestamp, None, str(last_error)


def read_grid(path: Path) -> Dict[Point, float]:
    points: Dict[Point, float] = {}
    with path.open(encoding="utf-8", errors="replace") as source:
        for line in source:
            fields = line.split()
            if len(fields) != 3:
                continue
            try:
                mlt, mlat, value = (float(field) for field in fields)
            except ValueError:
                continue
            if all(math.isfinite(number) for number in (mlt, mlat, value)):
                points[(mlt, mlat)] = value
    if len(points) != 7680:
        raise ValueError("{} has {} grid points; expected the 7,680-point sample layout".format(path.name, len(points)))
    return points


def quartiles(values: Sequence[float]) -> Tuple[Optional[float], Optional[float]]:
    if len(values) < 2:
        return None, None
    result = statistics.quantiles(values, n=4, method="exclusive")
    return result[0], result[2]


def fmt(value: Optional[float]) -> str:
    return "n/a" if value is None else "{:.5g}".format(value)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observations", required=True, type=Path)
    parser.add_argument("--kp-json", required=True, type=Path)
    parser.add_argument("--archive-indexes", required=True, type=Path)
    parser.add_argument("--grids", required=True, type=Path)
    parser.add_argument("--candidate-cutoff", default="2025-01-01T00:00:00Z")
    parser.add_argument("--download", action="store_true", help="download indexed snapshots needed by in-range reports")
    parser.add_argument("--workers", type=int, default=8)
    args = parser.parse_args()
    for path in (args.observations, args.kp_json, args.archive_indexes):
        if not path.exists():
            parser.error("input does not exist: {}".format(path))
    if args.workers < 1 or args.workers > 16:
        parser.error("--workers must be from 1 to 16")

    archive = load_indexes(args.archive_indexes)
    cutoff = parse_utc(args.candidate_cutoff)
    context = dt.timedelta(hours=CONTEXT_BY_GAP[48])
    cores = group_cores(storm_cores(load_kp(args.kp_json)), dt.timedelta(hours=48))
    windows = [(start - context, end + context) for start, end in cores if end + context > cutoff]

    report_rows: List[Tuple[int, dt.datetime, str, float, float]] = []
    counts: Dict[str, Counter] = defaultdict(Counter)
    with args.observations.open(newline="", encoding="utf-8-sig") as source:
        for row in csv.DictReader(source):
            try:
                start = parse_utc(row["time_start"])
                end = parse_utc(row["time_end"])
                if end < start:
                    continue
                midpoint = start + (end - start) / 2
                mlat = float(row["mlat"])
                mlt = float(row["mlt"]) % 24.0
                if not all(math.isfinite(value) for value in (mlat, mlt)):
                    continue
            except (KeyError, TypeError, ValueError, OverflowError):
                continue
            event_id = next((index for index, (begin, finish) in enumerate(windows) if begin <= midpoint < finish), None)
            if event_id is None:
                continue
            label = observation_label(row)
            counts[label]["reports"] += 1
            if label == "excluded_missing_or_other_sky":
                continue
            report_rows.append((event_id, midpoint, label, mlt, mlat))

    # The archive's southern-hemisphere coverage is not inferred from the northern sample grid.
    eligible = [row for row in report_rows if 50.0 <= row[4] <= 89.5]
    needed = sorted({rounded_snapshot(row[1]) for row in eligible})
    indexed = [timestamp for timestamp in needed if timestamp in archive]
    missing_index = len(needed) - len(indexed)
    args.grids.mkdir(parents=True, exist_ok=True)
    download_failures = 0
    if args.download:
        tasks = [(timestamp, archive[timestamp], args.grids) for timestamp in indexed]
        errors: List[str] = []
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = [pool.submit(download_one, task) for task in tasks]
            for future in as_completed(futures):
                timestamp, path, error = future.result()
                if error:
                    errors.append("{}: {}".format(timestamp.isoformat(), error))
        download_failures = len(errors)
        if errors:
            print("download_failures={} (first 5 shown)".format(len(errors)))
            for error in errors[:5]:
                print(error)
    else:
        print("download disabled; use --download to fetch indexed snapshots")

    cache: Dict[Path, Dict[Point, float]] = {}
    template_mlt: Optional[Set[float]] = None
    template_mlat: Optional[Set[float]] = None
    matched: Dict[str, List[float]] = defaultdict(list)
    event_values: Dict[Tuple[int, str], List[float]] = defaultdict(list)
    missing_grid = Counter()
    negative_mlat = Counter()
    positive_mlat_outside = Counter()
    invalid_snapshot = Counter()
    for event_id, timestamp, label, mlt, mlat in report_rows:
        if mlat < 0.0:
            negative_mlat[label] += 1
            continue
        if not 50.0 <= mlat <= 89.5:
            positive_mlat_outside[label] += 1
            continue
        snap = rounded_snapshot(timestamp)
        relative = archive.get(snap)
        if relative is None:
            invalid_snapshot[label] += 1
            continue
        path = args.grids / Path(relative).name
        if not path.is_file():
            missing_grid[label] += 1
            continue
        if path not in cache:
            cache[path] = read_grid(path)
        grid = cache[path]
        mlt_values = {coordinate[0] for coordinate in grid}
        mlat_values = {coordinate[1] for coordinate in grid}
        expected_mlt = {index * 0.25 for index in range(96)}
        expected_mlat = {50.0 + index * 0.5 for index in range(80)}
        if mlt_values != expected_mlt or mlat_values != expected_mlat:
            raise ValueError("coordinate axes differ from the sample grid in {}".format(path.name))
        if template_mlt is None:
            template_mlt, template_mlat = mlt_values, mlat_values
        elif mlt_values != template_mlt or mlat_values != template_mlat:
            raise ValueError("coordinate axes changed within the archive at {}".format(path.name))
        cell = (nearest_axis(mlt_values, mlt, circular=True), nearest_axis(mlat_values, mlat))
        value = grid[cell]
        matched[label].append(value)
        event_values[(event_id, label)].append(value)

    for label in ("seen", "clear_not_seen"):
        expected = counts[label]["reports"]
        values = matched[label]
        q25, q75 = quartiles(values)
        print("{}: reports={} negative_MLat_unmatched={} positive_MLat_outside_grid={} archive_index_missing={} grid_not_downloaded={} matched={} median={} p25={} p75={} zero_pct={}".format(
            label, expected, negative_mlat[label], positive_mlat_outside[label], invalid_snapshot[label], missing_grid[label], len(values),
            fmt(statistics.median(values) if values else None), fmt(q25), fmt(q75),
            fmt(100.0 * sum(value == 0 for value in values) / len(values) if values else None),
        ))
    paired = sum(
        bool(event_values[(event_id, "seen")]) and bool(event_values[(event_id, "clear_not_seen")])
        for event_id in range(len(windows))
    )
    paired_medians = []
    for event_id in range(len(windows)):
        seen = event_values[(event_id, "seen")]
        clear = event_values[(event_id, "clear_not_seen")]
        if seen and clear:
            paired_medians.append(statistics.median(seen) - statistics.median(clear))
    print("candidate_windows={} windows_with_both_matched_labels={} report_weighted_median_difference_seen_minus_clear={} event_median_difference_median={}".format(
        len(windows), paired,
        fmt(statistics.median(matched["seen"]) - statistics.median(matched["clear_not_seen"]) if matched["seen"] and matched["clear_not_seen"] else None),
        fmt(statistics.median(paired_medians) if paired_medians else None),
    ))
    print("unique_needed_snapshots={} indexed={} index_missing={} download_failures={} grid_files_read={}. Values are raw model outputs, not probabilities; no threshold or performance score is produced.".format(
        len(needed), len(indexed), missing_index, download_failures, len(cache)
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
