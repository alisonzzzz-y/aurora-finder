#!/usr/bin/env python3
"""Aggregate Kp storm-window coverage against Aurorasaurus observations.

This exploratory audit creates candidate event windows from independent, definitive
three-hour Kp intervals. It prints aggregate counts only and does not validate an
aurora-viewing rule or estimate probabilities.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable

UTC = dt.timezone.utc
INTERVAL = dt.timedelta(hours=3)
GAPS_HOURS = (24, 48, 72)
CONTEXT_BY_GAP = {24: 12, 48: 24, 72: 36}


def parse_utc(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    return parsed.replace(tzinfo=UTC) if parsed.tzinfo is None else parsed.astimezone(UTC)


def observation_label(row: dict[str, str]) -> str:
    seen = row.get("see_aurora", "").strip().lower()
    sky = row.get("sky_id", "").strip().lower()
    if seen == "true":
        return "seen"
    if seen == "false" and sky == "clea":
        return "clear_not_seen"
    return "excluded_missing_or_other_sky"


def load_kp(path: Path) -> list[tuple[dt.datetime, float]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    times, values, statuses = payload["datetime"], payload["Kp"], payload["status"]
    if not (len(times) == len(values) == len(statuses)):
        raise ValueError("GFZ datetime, Kp, and status arrays must have equal lengths")
    records = []
    for timestamp, value, status in zip(times, values, statuses):
        if status != "def" or value is None:
            continue
        records.append((parse_utc(timestamp), float(value)))
    records.sort()
    return records


def load_observations(path: Path) -> list[tuple[dt.datetime, str]]:
    records = []
    with path.open(newline="", encoding="utf-8-sig") as source:
        for row in csv.DictReader(source):
            start, end = parse_utc(row["time_start"]), parse_utc(row["time_end"])
            if end < start:
                continue
            records.append((start + (end - start) / 2, observation_label(row)))
    return records


def storm_cores(kp: Iterable[tuple[dt.datetime, float]]) -> list[tuple[dt.datetime, dt.datetime]]:
    return [(timestamp, timestamp + INTERVAL) for timestamp, value in kp if value >= 5.0]


def group_cores(
    cores: Iterable[tuple[dt.datetime, dt.datetime]], quiet_gap: dt.timedelta
) -> list[tuple[dt.datetime, dt.datetime]]:
    events: list[list[dt.datetime]] = []
    for start, end in cores:
        if events and start - events[-1][1] <= quiet_gap:
            events[-1][1] = max(events[-1][1], end)
        else:
            events.append([start, end])
    return [(start, end) for start, end in events]


def summarize(
    events: list[tuple[dt.datetime, dt.datetime]],
    observations: list[tuple[dt.datetime, str]],
    context: dt.timedelta,
) -> tuple[Counter[str], dict[int, Counter[str]], int]:
    total: Counter[str] = Counter()
    annual: dict[int, Counter[str]] = defaultdict(Counter)
    assigned = 0
    event_index = 0
    events = [(start - context, end + context) for start, end in events]
    for timestamp, label in sorted(observations):
        while event_index < len(events) and timestamp >= events[event_index][1]:
            event_index += 1
        if event_index < len(events) and events[event_index][0] <= timestamp < events[event_index][1]:
            assigned += 1
            total[label] += 1
            annual[timestamp.year][label] += 1
    return total, annual, assigned


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observations", required=True, type=Path)
    parser.add_argument("--kp-json", required=True, type=Path)
    parser.add_argument(
        "--candidate-cutoff", default="2025-01-01T00:00:00Z",
        help="UTC boundary for a proposed temporal split; overlapping event windows go to holdout",
    )
    args = parser.parse_args()
    if not args.observations.is_file() or not args.kp_json.is_file():
        parser.error("both input files must exist")

    kp = load_kp(args.kp_json)
    observations = load_observations(args.observations)
    if not kp:
        parser.error("no definitive Kp intervals found")
    if any(kp[index][0] - kp[index - 1][0] != INTERVAL for index in range(1, len(kp))):
        print("Warning: definitive Kp sequence contains missing or irregular intervals.")
    cores = storm_cores(kp)
    print("definitive_kp_intervals={} range={}..{} threshold=Kp>=5".format(
        len(kp), kp[0][0].isoformat(), (kp[-1][0] + INTERVAL).isoformat()
    ))
    print("observations={} storm_core_intervals={}".format(len(observations), len(cores)))

    for gap_hours in GAPS_HOURS:
        events = group_cores(cores, dt.timedelta(hours=gap_hours))
        context = dt.timedelta(hours=CONTEXT_BY_GAP[gap_hours])
        total, annual, assigned = summarize(events, observations, context)
        profiles = []
        for start, end in events:
            counts = Counter(
                label for timestamp, label in observations
                if start - context <= timestamp < end + context
            )
            profiles.append((start.year, counts))
        both_labels = sum(counts["seen"] > 0 and counts["clear_not_seen"] > 0 for _, counts in profiles)
        print("gap_hours={} context_hours_each_side={} events={} both_label={} both_ge5={} both_ge10={} both_ge20={} assigned_reports={} unassigned_reports={}".format(
            gap_hours, CONTEXT_BY_GAP[gap_hours], len(events), both_labels,
            sum(counts["seen"] >= 5 and counts["clear_not_seen"] >= 5 for _, counts in profiles),
            sum(counts["seen"] >= 10 and counts["clear_not_seen"] >= 10 for _, counts in profiles),
            sum(counts["seen"] >= 20 and counts["clear_not_seen"] >= 20 for _, counts in profiles),
            assigned, len(observations) - assigned,
        ))
        print("  labels: seen={} clear_not_seen={} excluded={}".format(
            total["seen"], total["clear_not_seen"], total["excluded_missing_or_other_sky"]
        ))
        print("  year | events | seen | clear_not_seen")
        for year in sorted(annual):
            counts = annual[year]
            year_profiles = [profile for event_year, profile in profiles if event_year == year]
            event_count = len(year_profiles)
            print("  {} | {} | {} | {}".format(
                year, event_count, counts["seen"], counts["clear_not_seen"]
            ))
            if year in (2024, 2025):
                print("    events with >=5 seen and >=5 clear-not-seen: {}".format(
                    sum(profile["seen"] >= 5 and profile["clear_not_seen"] >= 5 for profile in year_profiles)
                ))

    cutoff = parse_utc(args.candidate_cutoff)
    candidate_gap = dt.timedelta(hours=48)
    candidate_context = dt.timedelta(hours=24)
    candidate_events = group_cores(cores, candidate_gap)
    development, holdout, straddling = [], [], 0
    for start, end in candidate_events:
        window_start, window_end = start - candidate_context, end + candidate_context
        if window_end <= cutoff:
            development.append((start, end))
        else:
            holdout.append((start, end))
            straddling += window_start < cutoff < window_end
    print("candidate_temporal_cutoff={} policy=48h quiet gap + 24h context; overlapping windows assigned wholly to holdout".format(cutoff.isoformat()))
    print("candidate event windows: development={} holdout={} boundary_straddlers={}".format(
        len(development), len(holdout), straddling
    ))
    for name, selected in (("development", development), ("holdout", holdout)):
        selected_counts = Counter()
        profiles = []
        for start, end in selected:
            counts = Counter(
                label for timestamp, label in observations
                if start - candidate_context <= timestamp < end + candidate_context
            )
            profiles.append(counts)
            selected_counts.update(counts)
        print("  {}: events={} seen={} clear_not_seen={} excluded={} both_label_events={} both_ge5_events={}".format(
            name, len(selected), selected_counts["seen"], selected_counts["clear_not_seen"],
            selected_counts["excluded_missing_or_other_sky"],
            sum(c["seen"] > 0 and c["clear_not_seen"] > 0 for c in profiles),
            sum(c["seen"] >= 5 and c["clear_not_seen"] >= 5 for c in profiles),
        ))
    print("Candidate windows only; they are not official storm IDs, a frozen holdout, or validation results.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
