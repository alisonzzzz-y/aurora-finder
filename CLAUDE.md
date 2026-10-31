# Aurora Finder: working agreement for Claude

## Default workflow (owner's standing instruction)

- The owner works **locally** on their Mac (`~/Desktop/aurora-finder`) and keeps **one ongoing conversation** for many requests. Do not suggest opening a new conversation or session for each request.
- Do not commit directly on `main`: it deploys automatically (Vercel frontend, Render backend). Put changes on a feature branch, push it, and open a pull request (or update the open one) without asking again. Then report the CI result (backend, frontend with desktop/mobile browser tests, MySQL).
- The owner merges the PR. Do not merge it yourself. After a merge, switch back to an up-to-date `main` before starting the next change.
- If the owner explicitly asks to push straight to `main`, do so.
- Reply to the owner in Chinese.

## Checks before pushing

- Full local check from the repository root: `bash scripts/check_local.sh` (run `npm ci` in `frontend` first).
- Browser tests: `cd frontend && npx playwright install chromium` once, then `npm run test:e2e`.
- MySQL: with Docker Desktop running, run `bash scripts/check_mysql_restore.sh`. Run `JdbcRunRecordStoreTest,JdbcPushSubscriptionStoreTest` with the `TEST_MYSQL_*` variables used in `.github/workflows/ci.yml`.
- Cloud sessions only: Docker is installed but not started, so run `nohup dockerd >/tmp/dockerd.log 2>&1 &`. If Docker Hub returns 429, pull `mirror.gcr.io/library/mysql:8.4` and tag it `mysql:8.4`. If Playwright's bundled browser is missing, point `launchOptions.executablePath` at `/opt/pw-browsers/chromium` in an untracked config.

## Things that stay true

- The combined viewing rule is `NOT_VALIDATED`. Never present a personal probability of seeing aurora.
- Current status, limits and owner tasks are in `docs/completion-status-2026-10-26.md`; deployment settings are in `docs/operations.md`.
