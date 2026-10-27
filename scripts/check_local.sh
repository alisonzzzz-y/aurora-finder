#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root/frontend"
npm run lint
npm test
npm run build
if [[ "${BROWSER_TESTS:-0}" == "1" ]]; then npm run test:e2e; fi
cd "$project_root/backend"
./mvnw --batch-mode clean verify
cd "$project_root"
python3 -m unittest discover -s scripts -p 'test_*.py'
