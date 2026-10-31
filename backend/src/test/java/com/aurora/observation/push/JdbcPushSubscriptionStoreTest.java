package com.aurora.observation.push;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPushSubscriptionStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    void savesReplacesDeletesAndRecordsAlertsOnce() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:push;DB_CLOSE_DELAY=-1", "sa", ""));
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load().migrate();
        var store = new JdbcPushSubscriptionStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        String endpoint = "https://fcm.googleapis.com/fcm/send/abc";

        store.save(new PushSubscription(endpoint, "key-1", "auth-1", "en"));
        store.save(new PushSubscription(endpoint, "key-2", "auth-2", "zh"));
        assertEquals(1, store.count());
        assertEquals(new PushSubscription(endpoint, "key-2", "auth-2", "zh"), store.all().getFirst());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription WHERE endpoint_hash = ?", Integer.class,
                JdbcPushSubscriptionStore.hash(endpoint)));

        assertTrue(store.markAlertSent("current:2026-10-31T00:00:00Z:5"));
        assertFalse(store.markAlertSent("current:2026-10-31T00:00:00Z:5"));
        store.purgeAlertsBefore(NOW.plusSeconds(1));
        assertTrue(store.markAlertSent("current:2026-10-31T00:00:00Z:5"));

        store.delete(endpoint);
        assertEquals(0, store.count());
    }
}
