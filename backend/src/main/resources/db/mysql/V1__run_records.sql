CREATE TABLE evaluation_run (
    run_id VARCHAR(36) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    created_at_utc TIMESTAMP(6) NOT NULL,
    completed_at_utc TIMESTAMP(6),
    result_status VARCHAR(30) NOT NULL,
    location_ref BIGINT,
    rule_version VARCHAR(40) NOT NULL,
    rule_status VARCHAR(30) NOT NULL,
    coverage_status VARCHAR(30)
);

CREATE INDEX evaluation_run_created_at_idx ON evaluation_run(created_at_utc);

CREATE TABLE evaluation_night (
    run_id VARCHAR(36) NOT NULL,
    local_date DATE NOT NULL,
    window_start_utc TIMESTAMP(6) NOT NULL,
    window_end_utc TIMESTAMP(6) NOT NULL,
    level VARCHAR(30) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (run_id, local_date),
    FOREIGN KEY (run_id) REFERENCES evaluation_run(run_id) ON DELETE CASCADE
);

CREATE TABLE evaluation_source (
    run_id VARCHAR(36) NOT NULL,
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
    PRIMARY KEY (run_id, source_key),
    FOREIGN KEY (run_id) REFERENCES evaluation_run(run_id) ON DELETE CASCADE
);

CREATE TABLE assistant_tool_call (
    run_id VARCHAR(36) NOT NULL,
    sequence_no INTEGER NOT NULL,
    tool_name VARCHAR(50) NOT NULL,
    completed_at_utc TIMESTAMP(6) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    evidence_json LONGTEXT NOT NULL,
    PRIMARY KEY (run_id, sequence_no),
    FOREIGN KEY (run_id) REFERENCES evaluation_run(run_id) ON DELETE CASCADE
);
