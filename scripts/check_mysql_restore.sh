#!/usr/bin/env bash
# Disposable local MySQL drill. Never connects to Railway or an existing database.
set -euo pipefail
umask 077
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
container="aurora-restore-check-$$"
work_dir="$(mktemp -d)"
cleanup() { docker rm -fv "$container" >/dev/null 2>&1 || true; rm -rf "$work_dir"; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Synthetic credentials, no published ports, no production variables or mounted data.
docker run --rm -d --name "$container" -e MYSQL_ROOT_PASSWORD=ci-only-password mysql:8.4 >/dev/null
ready=0
for attempt in {1..90}; do
  if docker exec -e MYSQL_PWD=ci-only-password "$container" mysql -h127.0.0.1 -uroot -e 'SELECT 1' >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == "1" ]] || { echo 'FAIL: temporary MySQL did not become ready'; exit 1; }
mysql_cmd=(docker exec -i -e MYSQL_PWD=ci-only-password "$container" mysql -uroot --default-character-set=utf8mb4)
dump_cmd=(docker exec -e MYSQL_PWD=ci-only-password "$container" mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --no-tablespaces --set-gtid-purged=OFF)
"${mysql_cmd[@]}" -e 'CREATE DATABASE original_check CHARACTER SET utf8mb4; CREATE DATABASE restored_check CHARACTER SET utf8mb4;'
for migration in "$root"/backend/src/main/resources/db/mysql/V*.sql; do
  "${mysql_cmd[@]}" original_check < "$migration"
done
"${mysql_cmd[@]}" original_check <<'SQL'
SET time_zone = '+00:00';
INSERT INTO evaluation_run(run_id,kind,created_at_utc,result_status,rule_version,rule_status,location_ref)
VALUES ('restore-proof','PAGE_FACTS','2026-10-29 00:15:00.123456','COMPLETED','test','NOT_VALIDATED',2964574);
INSERT INTO evaluation_night(run_id,snapshot_no,location_ref,local_date,window_start_utc,window_end_utc,level,reason_code)
VALUES ('restore-proof',1,2964574,'2026-10-29','2026-10-29 18:00:00','2026-10-30 06:00:00','INSUFFICIENT_DATA','RULE_NOT_VALIDATED'),
       ('restore-proof',2,2964574,'2026-10-30','2026-10-30 18:00:00','2026-10-31 06:00:00','INSUFFICIENT_DATA','RULE_NOT_VALIDATED');
INSERT INTO evaluation_source(run_id,snapshot_no,location_ref,source_key,fetch_status,time_scope,evidence_json)
VALUES ('restore-proof',1,2964574,'cloud','CURRENT','SHORT_RANGE','{"cloud":0.25,"label":"都柏林 ☁"}'),
       ('restore-proof',2,2964574,'cloud','UNAVAILABLE','SHORT_RANGE',NULL);
INSERT INTO assistant_tool_call(run_id,sequence_no,tool_name,completed_at_utc,outcome,evidence_json)
VALUES ('restore-proof',1,'restore_probe','2026-10-29 00:15:01.654321','OK','{"synthetic":true}');
INSERT INTO observation_history(location_ref,bucket_utc,captured_at_utc,snapshot_json)
VALUES (2964574,1793232900,'2026-10-29 00:15:00','{"locationName":"都柏林","cloudCoverPercent":0.25,"synthetic":true}'),
       (2964574,1793233800,'2026-10-29 00:30:00','{"locationName":"都柏林","cloudCoverPercent":null,"synthetic":true}');
SQL

"${dump_cmd[@]}" original_check > "$work_dir/backup.sql"
[[ -s "$work_dir/backup.sql" ]] || { echo 'FAIL: empty backup'; exit 1; }
"${mysql_cmd[@]}" restored_check < "$work_dir/backup.sql"

# Compare every table's schema, index/constraint definitions and ordered row values.
# Dumps stay local and are deleted by the trap, including on failure.
for database in original_check restored_check; do
  "${dump_cmd[@]}" --no-data --skip-comments --compact "$database" > "$work_dir/$database.schema"
  "${dump_cmd[@]}" --no-create-info --skip-comments --compact --skip-add-locks --skip-disable-keys --order-by-primary --hex-blob "$database" > "$work_dir/$database.rows"
done
cmp -s "$work_dir/original_check.schema" "$work_dir/restored_check.schema" || { echo 'FAIL: restored schema differs'; exit 1; }
cmp -s "$work_dir/original_check.rows" "$work_dir/restored_check.rows" || { echo 'FAIL: restored row values differ'; exit 1; }
echo 'PASS: all project tables, indexes, constraints and row values survived dump and restore.'

query() { "${mysql_cmd[@]}" -N -B restored_check -e "$1"; }
[[ "$(query "SELECT COUNT(*) FROM evaluation_run r JOIN evaluation_night n USING(run_id) JOIN evaluation_source s ON s.run_id=n.run_id AND s.snapshot_no=n.snapshot_no JOIN assistant_tool_call t ON t.run_id=r.run_id;")" == 2 ]] || { echo 'FAIL: linked snapshots missing'; exit 1; }
[[ "$(query "SELECT COUNT(*) FROM evaluation_source WHERE evidence_json IS NULL;")" == 1 ]] || { echo 'FAIL: missing source was changed'; exit 1; }
# Check foreign keys are enforced after restore, not just present in the SQL text.
if query "INSERT INTO assistant_tool_call VALUES ('missing-parent',1,'probe',UTC_TIMESTAMP(6),'OK','{}');" > /dev/null 2>&1; then
  echo 'FAIL: restored database accepted an orphan tool record'; exit 1
fi
query "DELETE FROM evaluation_run WHERE run_id='restore-proof';" >/dev/null
[[ "$(query 'SELECT (SELECT COUNT(*) FROM evaluation_night)+(SELECT COUNT(*) FROM evaluation_source)+(SELECT COUNT(*) FROM assistant_tool_call);')" == 0 ]] || { echo 'FAIL: restored cascade cleanup failed'; exit 1; }
[[ "$(query 'SELECT COUNT(*) FROM observation_history;')" == 2 ]] || { echo 'FAIL: independent public history was lost'; exit 1; }
echo 'PASS: restored relationships, missing values and cascade cleanup work; public history remains intact.'
