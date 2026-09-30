CREATE TABLE evaluation_run (
    run_id VARCHAR(36) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    created_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at_utc TIMESTAMP WITH TIME ZONE,
    result_status VARCHAR(30) NOT NULL,
    location_ref BIGINT,
    rule_version VARCHAR(40) NOT NULL,
    rule_status VARCHAR(30) NOT NULL,
    coverage_status VARCHAR(30)
);

CREATE INDEX evaluation_run_created_at_idx ON evaluation_run(created_at_utc);

CREATE TABLE evaluation_night (
    run_id VARCHAR(36) NOT NULL REFERENCES evaluation_run(run_id) ON DELETE CASCADE,
    local_date DATE NOT NULL,
    window_start_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    level VARCHAR(30) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (run_id, local_date)
);

CREATE TABLE evaluation_source (
    run_id VARCHAR(36) NOT NULL REFERENCES evaluation_run(run_id) ON DELETE CASCADE,
    source_key VARCHAR(30) NOT NULL,
    fetch_status VARCHAR(30) NOT NULL,
    time_scope VARCHAR(30) NOT NULL,
    failure_code VARCHAR(40),
    retrieved_at_utc TIMESTAMP WITH TIME ZONE,
    observed_at_utc TIMESTAMP WITH TIME ZONE,
    forecast_at_utc TIMESTAMP WITH TIME ZONE,
    scope_start_utc TIMESTAMP WITH TIME ZONE,
    scope_end_utc TIMESTAMP WITH TIME ZONE,
    evidence_json TEXT,
    PRIMARY KEY (run_id, source_key)
);

CREATE TABLE assistant_tool_call (
    run_id VARCHAR(36) NOT NULL REFERENCES evaluation_run(run_id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    tool_name VARCHAR(50) NOT NULL,
    completed_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    evidence_json TEXT NOT NULL,
    PRIMARY KEY (run_id, sequence_no)
);
