ALTER TABLE evaluation_night RENAME TO evaluation_night_previous;

CREATE TABLE evaluation_night (
    run_id VARCHAR(36) NOT NULL,
    snapshot_no INTEGER NOT NULL,
    location_ref BIGINT,
    local_date DATE NOT NULL,
    window_start_utc TIMESTAMP(6) NOT NULL,
    window_end_utc TIMESTAMP(6) NOT NULL,
    level VARCHAR(30) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (run_id, snapshot_no, local_date),
    FOREIGN KEY (run_id) REFERENCES evaluation_run(run_id) ON DELETE CASCADE
);

INSERT INTO evaluation_night (run_id, local_date, window_start_utc, window_end_utc, level, reason_code, snapshot_no, location_ref) SELECT p.run_id, p.local_date, p.window_start_utc, p.window_end_utc, p.level, p.reason_code, 1, r.location_ref FROM evaluation_night_previous p JOIN evaluation_run r ON r.run_id = p.run_id;

DROP TABLE evaluation_night_previous;

ALTER TABLE evaluation_source RENAME TO evaluation_source_previous;

CREATE TABLE evaluation_source (
    run_id VARCHAR(36) NOT NULL,
    snapshot_no INTEGER NOT NULL,
    location_ref BIGINT,
    source_key VARCHAR(30) NOT NULL,
    fetch_status VARCHAR(30) NOT NULL,
    time_scope VARCHAR(30) NOT NULL,
    failure_code VARCHAR(40),
    retrieved_at_utc TIMESTAMP(6),
    observed_at_utc TIMESTAMP(6),
    forecast_at_utc TIMESTAMP(6),
    scope_start_utc TIMESTAMP(6),
    scope_end_utc TIMESTAMP(6),
    evidence_json LONGTEXT,
    PRIMARY KEY (run_id, snapshot_no, source_key),
    FOREIGN KEY (run_id) REFERENCES evaluation_run(run_id) ON DELETE CASCADE
);

INSERT INTO evaluation_source (run_id, source_key, fetch_status, time_scope, failure_code, retrieved_at_utc, observed_at_utc, forecast_at_utc, scope_start_utc, scope_end_utc, evidence_json, snapshot_no, location_ref) SELECT p.run_id, p.source_key, p.fetch_status, p.time_scope, p.failure_code, p.retrieved_at_utc, p.observed_at_utc, p.forecast_at_utc, p.scope_start_utc, p.scope_end_utc, p.evidence_json, 1, r.location_ref FROM evaluation_source_previous p JOIN evaluation_run r ON r.run_id = p.run_id;

DROP TABLE evaluation_source_previous;
