#!/usr/bin/env bash
# Disposable local MySQL drill. Never connects to Railway or another existing database.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
container="aurora-restore-check-$$"
dump_file="$(mktemp)"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; rm -f "$dump_file"; }
trap cleanup EXIT
# This password only protects the disposable container, which publishes no network ports.
docker run -d --name "$container" -e MYSQL_ROOT_PASSWORD=ci-only-password mysql:8.4 >/dev/null
ready=0
for attempt in {1..60}; do
  if docker exec "$container" mysql -h127.0.0.1 -uroot -pci-only-password -e "SELECT 1" >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == "1" ]] || { echo 'Temporary MySQL did not become ready'; exit 1; }
mysql_cmd=(docker exec -i -e MYSQL_PWD=ci-only-password "$container" mysql -uroot)
"${mysql_cmd[@]}" -e 'CREATE DATABASE original_check; CREATE DATABASE restored_check;'
for migration in "$root"/backend/src/main/resources/db/mysql/V*.sql; do
  "${mysql_cmd[@]}" original_check < "$migration"
done
"${mysql_cmd[@]}" original_check <<'SQL'
INSERT INTO evaluation_run(run_id,kind,created_at_utc,result_status,rule_version,rule_status)
VALUES ('restore-proof','PERSISTENCE_CHECK',UTC_TIMESTAMP(6),'COMPLETED','test','NOT_VALIDATED');
INSERT INTO assistant_tool_call(run_id,sequence_no,tool_name,completed_at_utc,outcome,evidence_json)
VALUES ('restore-proof',1,'restore_probe',UTC_TIMESTAMP(6),'OK','{}');
SQL
docker exec -e MYSQL_PWD=ci-only-password "$container" mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF original_check > "$dump_file"
"${mysql_cmd[@]}" restored_check < "$dump_file"
count="$("${mysql_cmd[@]}" -N -B restored_check -e "SELECT COUNT(*) FROM evaluation_run r JOIN assistant_tool_call t USING(run_id) WHERE r.run_id='restore-proof' AND t.tool_name='restore_probe';")"
[[ "$count" == "1" ]] || { echo 'Restore verification failed'; exit 1; }
echo 'PASS: disposable MySQL schema and linked evidence survived dump and restore.'
