# Aurora Finder: working agreement for Claude

## Default workflow (owner's standing instruction)

- Make code changes in a **cloud session on its own branch**, never directly on `main`. `main` deploys automatically (Vercel frontend, Render backend).
- When the work is ready: commit, push the branch, and **open a pull request** without asking again. Wait for CI (backend, frontend with desktop/mobile browser tests, MySQL) and report the result.
- The owner merges the PR. Do not merge it yourself.
- Exception: a tiny change that cannot affect running code (a typo, wording in docs) may go straight to `main`, but only when the owner asks for that.
- Use local sessions on the owner's Mac only for real-environment checks: real camera playback, push notifications after VAPID keys are set, live provider data. On one branch, change files either in the cloud or locally, never both at once.
- Reply to the owner in Chinese.

## Checks before pushing

- Full local check from the repository root: `bash scripts/check_local.sh` (run `npm ci` in `frontend` first).
- Browser tests: `cd frontend && npm run test:e2e`. In a cloud container, Playwright's bundled browser may be missing; point `launchOptions.executablePath` at `/opt/pw-browsers/chromium` with an untracked local config.
- MySQL: Docker is installed in the cloud container but not started. Run `nohup dockerd >/tmp/dockerd.log 2>&1 &`. If Docker Hub returns 429, pull `mirror.gcr.io/library/mysql:8.4` and tag it `mysql:8.4`. Then run `bash scripts/check_mysql_restore.sh`, and run `JdbcRunRecordStoreTest,JdbcPushSubscriptionStoreTest` with the `TEST_MYSQL_*` variables used in `.github/workflows/ci.yml`.

## Things that stay true

- The combined viewing rule is `NOT_VALIDATED`. Never present a personal probability of seeing aurora.
- Current status, limits and owner tasks are in `docs/completion-status-2026-10-26.md`; deployment settings are in `docs/operations.md`.
