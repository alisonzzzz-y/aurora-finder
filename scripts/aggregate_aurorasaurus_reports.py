#!/usr/bin/env python3
"""Generate yearly aggregate counts from the Aurorasaurus cleaned CSV.

The source CSV is intentionally kept outside the repository. Only yearly totals
are written to the frontend dataset; this script never emits row-level data.
"""

import argparse
import csv
import hashlib
import json
from collections import Counter
from datetime import datetime
from pathlib import Path

EXPECTED_MD5 = "2319009e2e2f3dfb596ad5368e719860"
EXPECTED_ROWS = 22_280
SOURCE_NAME = "web_observations_2014-08-01_to_2025-08-02_cleaned.csv"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True, help="Path to the cleaned CSV, stored outside this repository")
    parser.add_argument(
        "--output", type=Path,
        default=Path("frontend/src/data/auroraHistorySummary.json"),
        help="Path for the annual aggregate JSON",
    )
    args = parser.parse_args()

    digest = hashlib.md5(args.input.read_bytes()).hexdigest()
    if digest != EXPECTED_MD5:
        raise SystemExit(f"Unexpected source MD5: {digest}")

    counts: Counter[int] = Counter()
    starts: list[datetime] = []
    total = 0
    with args.input.open(newline="", encoding="utf-8-sig") as csv_file:
        rows = csv.DictReader(csv_file)
        if not rows.fieldnames or "time_start" not in rows.fieldnames:
            raise SystemExit("CSV is missing required time_start field")
        for row in rows:
            raw_time = (row.get("time_start") or "").strip()
            try:
                observed_at = datetime.fromisoformat(raw_time.replace("Z", "+00:00"))
            except ValueError as error:
                raise SystemExit(f"Invalid time_start value at row {total + 2}") from error
            counts[observed_at.year] += 1
            starts.append(observed_at)
            total += 1

    if total != EXPECTED_ROWS:
        raise SystemExit(f"Unexpected row count: {total}")

    summary = {
        "source": "Aurorasaurus Web Observations (2014-2025), cleaned CSV",
        "sourceUrl": "https://doi.org/10.5281/zenodo.16783265",
        "sourceFile": SOURCE_NAME,
        "sourceMd5": EXPECTED_MD5,
        "totalReports": total,
        "coverageStart": min(starts).date().isoformat(),
        "coverageEnd": max(starts).date().isoformat(),
        "years": [{"year": year, "reports": counts[year]} for year in range(min(counts), max(counts) + 1)],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(summary['years'])} yearly totals ({total} reports) to {args.output}")


if __name__ == "__main__":
    main()
