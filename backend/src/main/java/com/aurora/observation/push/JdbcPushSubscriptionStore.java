package com.aurora.observation.push;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public class JdbcPushSubscriptionStore implements PushSubscriptionStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public JdbcPushSubscriptionStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.clock = clock;
    }

    @Override public int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class);
    }

    @Override public void save(PushSubscription subscription) {
        String hash = hash(subscription.endpoint());
        // Delete then insert keeps the upsert portable between MySQL and H2.
        transaction.executeWithoutResult(ignored -> {
            jdbc.update("DELETE FROM push_subscription WHERE endpoint_hash = ?", hash);
            jdbc.update("INSERT INTO push_subscription(endpoint_hash, endpoint, p256dh, auth_secret, language, created_at_utc) VALUES (?, ?, ?, ?, ?, ?)",
                    hash, subscription.endpoint(), subscription.p256dh(), subscription.auth(), subscription.language(),
                    Timestamp.from(clock.instant()));
        });
    }

    @Override public void delete(String endpoint) {
        jdbc.update("DELETE FROM push_subscription WHERE endpoint_hash = ?", hash(endpoint));
    }

    @Override public List<PushSubscription> all() {
        return jdbc.query("SELECT endpoint, p256dh, auth_secret, language FROM push_subscription ORDER BY created_at_utc",
                (row, number) -> new PushSubscription(row.getString(1), row.getString(2), row.getString(3), row.getString(4)));
    }

    @Override public boolean markAlertSent(String alertKey) {
        try {
            jdbc.update("INSERT INTO push_alert(alert_key, sent_at_utc) VALUES (?, ?)", alertKey, Timestamp.from(clock.instant()));
            return true;
        } catch (DuplicateKeyException alreadySent) {
            return false;
        }
    }

    @Override public void purgeAlertsBefore(Instant cutoff) {
        jdbc.update("DELETE FROM push_alert WHERE sent_at_utc < ?", Timestamp.from(cutoff));
    }

    static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
