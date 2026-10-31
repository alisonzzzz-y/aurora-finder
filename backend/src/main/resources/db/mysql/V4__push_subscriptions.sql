-- Browser push subscriptions for strong-activity alerts. Endpoints are capability URLs, so they are
-- addressed by their SHA-256 hash and never logged.
CREATE TABLE push_subscription (
    endpoint_hash CHAR(64) PRIMARY KEY,
    endpoint VARCHAR(1024) NOT NULL,
    p256dh VARCHAR(128) NOT NULL,
    auth_secret VARCHAR(64) NOT NULL,
    language VARCHAR(8) NOT NULL,
    created_at_utc TIMESTAMP(6) NOT NULL
);

-- One row per alert already broadcast, so a period and level is pushed only once.
CREATE TABLE push_alert (
    alert_key VARCHAR(80) PRIMARY KEY,
    sent_at_utc TIMESTAMP(6) NOT NULL
);

CREATE INDEX push_alert_sent_at_idx ON push_alert(sent_at_utc);
