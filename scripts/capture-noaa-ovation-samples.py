#!/usr/bin/env python3
"""Capture auditable NOAA OVATION JSON samples at a fixed interval."""

import argparse
import gzip
import hashlib
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen


SOURCE = "https://services.swpc.noaa.gov/json/ovation_aurora_latest.json"


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, default=3)
    parser.add_argument("--interval-seconds", type=int, default=300)
    parser.add_argument("--output", type=Path, default=Path("docs/data/noaa-ovation-samples"))
    args = parser.parse_args()
    if args.count < 2 or args.interval_seconds < 1:
        parser.error("count must be at least 2 and interval-seconds must be positive")

    args.output.mkdir(parents=True, exist_ok=True)
    manifest_path = args.output / "manifest.json"
    manifest = {"source": SOURCE, "samples": []}
    for index in range(args.count):
        if index:
            time.sleep(args.interval_seconds)
        request = Request(SOURCE, headers={"Accept": "application/json", "User-Agent": "AuroraFinder/0.1 (sample validation)"})
        with urlopen(request, timeout=20) as response:
            body = response.read()
            headers = response.headers
            status = response.status
        payload = json.loads(body)
        if status != 200 or not isinstance(payload, dict):
            raise RuntimeError(f"Unexpected NOAA response status or JSON structure: {status}")
        required = ("Observation Time", "Forecast Time", "Data Format", "coordinates")
        if any(field not in payload for field in required) or not isinstance(payload["coordinates"], list):
            raise RuntimeError("NOAA response is missing required OVATION fields")
        fetched_at = utc_now()
        digest = hashlib.sha256(body).hexdigest()
        filename = f"ovation-{fetched_at.replace(':', '').replace('-', '')}.json.gz"
        with (args.output / filename).open("wb") as compressed_file:
            with gzip.GzipFile(fileobj=compressed_file, mode="wb", compresslevel=9, mtime=0) as output:
                output.write(body)
        manifest["samples"].append({
            "fetchedAt": fetched_at,
            "source": SOURCE,
            "httpStatus": status,
            "lastModified": headers.get("Last-Modified"),
            "etag": headers.get("ETag"),
            "observationTime": payload["Observation Time"],
            "forecastTime": payload["Forecast Time"],
            "dataFormat": payload["Data Format"],
            "coordinateCount": len(payload["coordinates"]),
            "responseBytes": len(body),
            "sha256": digest,
            "rawGzip": filename,
        })
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(manifest["samples"][-1]), flush=True)


if __name__ == "__main__":
    main()
