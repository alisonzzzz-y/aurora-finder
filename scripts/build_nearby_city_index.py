#!/usr/bin/env python3
"""Build the compact GeoNames CC BY 4.0 city index used on demand by the UI."""
import io
import json
import urllib.request
import zipfile
from pathlib import Path

SOURCE = 'https://download.geonames.org/export/dump/cities15000.zip'
ROOT = Path(__file__).resolve().parents[1]

def main():
    with urllib.request.urlopen(SOURCE, timeout=60) as response:
        archive = response.read()
    with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
        lines = zipped.read('cities15000.txt').decode('utf-8').splitlines()
    rows = []
    for line in lines:
        f = line.split('\t')
        if len(f) < 19 or not f[17]:
            continue
        rows.append([int(f[0]), f[1], f[8], float(f[4]), float(f[5]), f[17]])
    rows.sort(key=lambda row: row[0])
    output = ROOT / 'frontend/public/data/nearby-cities.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'source': SOURCE, 'license': 'CC BY 4.0', 'columns': ['id', 'name', 'countryCode', 'latitude', 'longitude', 'timezone'], 'cities': rows}, ensure_ascii=False, separators=(',', ':')) + '\n')
    print(f'{len(rows)} cities, {output.stat().st_size} bytes')

if __name__ == '__main__':
    main()
