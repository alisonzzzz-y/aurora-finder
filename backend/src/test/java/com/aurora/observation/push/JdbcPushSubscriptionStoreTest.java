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
    // Set TEST_MYSQL_URL to run against MySQL, as the CI MySQL job does; otherwise H2 is used.
    private final String mysqlUrl = System.getenv("TEST_MYSQL_URL");
    private String mysqlSchema;

    private JdbcTemplate database() {
        if (mysqlUrl == null) return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:push;DB_CLOSE_DELAY=-1", "sa", ""));
        mysqlSchema = "push_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        mysqlAdmin().execute("CREATE DATABASE " + mysqlSchema);
        return new JdbcTemplate(new DriverManagerDataSource(mysqlUrl.replaceFirst("/[^/?]+(?=\\?|$)", "/" + mysqlSchema),
                System.getenv("TEST_MYSQL_USER"), System.getenv("TEST_MYSQL_PASSWORD")));
    }

    private JdbcTemplate mysqlAdmin() {
        return new JdbcTemplate(new DriverManagerDataSource(mysqlUrl, System.getenv("TEST_MYSQL_USER"), System.getenv("TEST_MYSQL_PASSWORD")));
    }

    @org.junit.jupiter.api.AfterEach
    void dropMysqlSchema() {
        if (mysqlSchema != null) mysqlAdmin().execute("DROP DATABASE " + mysqlSchema);
    }

    @Test
    void savesReplacesDeletesAndRecordsAlertsOnce() {
        var jdbc = database();
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(mysqlUrl == null ? "classpath:db/migration" : "classpath:db/mysql").load().migrate();
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
