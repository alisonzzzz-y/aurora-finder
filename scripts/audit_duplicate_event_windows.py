#!/usr/bin/env python3
"""Print aggregate duplicate-candidate and report-window diagnostics only."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
from collections import Counter, defaultdict

UTC = dt.timezone.utc
SIGNATURE_COLUMNS = (
    "time_start", "time_end", "address_country", "address_state", "colors",
    "see_aurora", "height_id", "sky_id", "activities_id", "types",
    "on_going", "duration",
)
SAMPLE_DAYS = (
    "2015-03-18", "2016-08-30", "2017-11-07", "2018-03-15",
    "2022-03-31", "2023-12-01", "2024-05-11", "2024-10-10", "2025-06-02",
)


def parse_utc(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    return parsed.replace(tzinfo=UTC) if parsed.tzinfo is None else parsed.astimezone(UTC)


def label(row: dict[str, str]) -> str:
    seen = row.get("see_aurora", "").strip().lower()
    sky = row.get("sky_id", "").strip().lower()
    if seen == "true":
        return "seen"
    if seen == "false" and sky == "clea":
        return "clear_not_seen"
    return "excluded_missing_or_other_sky"


def report_windows(by_day: dict[str, Counter[str]], max_gap_days: int) -> list[list[str]]:
    windows: list[list[str]] = []
    current: list[str] = []
    for day in sorted(by_day):
        date = dt.date.fromisoformat(day)
        if current and (date - dt.date.fromisoformat(current[-1])).days > max_gap_days:
            windows.append(current)
            current = []
        current.append(day)
    if current:
        windows.append(current)
    return windows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("observations", type=argparse.FileType("r", encoding="utf-8-sig"))
    args = parser.parse_args()

    rows: list[dict[str, str]] = []
    with args.observations as source:
        for row in csv.DictReader(source):
            start, end = parse_utc(row["time_start"]), parse_utc(row["time_end"])
            if end < start:
                continue
            row["_utc_day"] = (start + (end - start) / 2).date().isoformat()
            row["_label"] = label(row)
            rows.append(row)

    groups: dict[tuple[str, ...], list[int]] = defaultdict(list)
    for index, row in enumerate(rows):
        groups[tuple(row.get(column, "") for column in SIGNATURE_COLUMNS)].append(index)
    duplicate_groups = [indices for indices in groups.values() if len(indices) > 1]
    candidate_rows = {index for group in duplicate_groups for index in group}

    by_year: dict[str, Counter[str]] = defaultdict(Counter)
    by_day: dict[str, Counter[str]] = defaultdict(Counter)
    unique_signatures: dict[tuple[str, str], set[tuple[str, ...]]] = defaultdict(set)
    for index, row in enumerate(rows):
        day, year, row_label = row["_utc_day"], row["_utc_day"][:4], row["_label"]
        by_year[year]["rows"] += 1
        by_day[day]["rows"] += 1
        by_day[day][row_label] += 1
        unique_signatures[(day, row_label)].add(
            tuple(row.get(column, "") for column in SIGNATURE_COLUMNS)
        )
        if index in candidate_rows:
            by_year[year]["candidate_rows"] += 1
            by_day[day]["candidate_rows"] += 1
            by_day[day]["candidate_" + row_label] += 1

    print("rows={} candidate_groups={} candidate_rows={} excess_rows={} largest_group={}".format(
        len(rows), len(duplicate_groups), len(candidate_rows),
        sum(len(group) - 1 for group in duplicate_groups),
        max((len(group) for group in duplicate_groups), default=0),
    ))
    print("year | rows | candidate rows | percent")
    for year in sorted(by_year):
        counts = by_year[year]
        print("{} | {} | {} | {:.2f}".format(
            year, counts["rows"], counts["candidate_rows"],
            100 * counts["candidate_rows"] / counts["rows"],
        ))

    print("UTC day | seen raw/signatures | clear-not-seen raw/signatures | candidate seen/clear rows")
    for day in SAMPLE_DAYS:
        counts = by_day[day]
        print("{} | {}/{} | {}/{} | {}/{}".format(
            day,
            counts["seen"], len(unique_signatures[(day, "seen")]),
            counts["clear_not_seen"], len(unique_signatures[(day, "clear_not_seen")]),
            counts["candidate_seen"], counts["candidate_clear_not_seen"],
        ))

    for gap in (1, 2):
        windows = report_windows(by_day, gap)
        ranked = sorted(windows, key=lambda window: sum(by_day[day]["rows"] for day in window), reverse=True)
        largest = "; ".join(
            "{}..{}:{} reports/{} dates".format(
                window[0], window[-1], sum(by_day[day]["rows"] for day in window), len(window)
            )
            for window in ranked[:5]
        )
        print("report-date windows (gap <= {} day): {}; largest: {}".format(gap, len(windows), largest))
    print("Diagnostics only: signatures are not confirmed duplicates; report-date windows are not storm events.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
