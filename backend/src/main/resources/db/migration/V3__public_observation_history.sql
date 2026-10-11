CREATE TABLE observation_history (
    location_ref BIGINT NOT NULL,
    bucket_utc BIGINT NOT NULL,
    captured_at_utc TIMESTAMP NOT NULL,
    snapshot_json TEXT NOT NULL,
    PRIMARY KEY (location_ref, bucket_utc)
);
CREATE INDEX observation_history_age_idx ON observation_history(captured_at_utc);
