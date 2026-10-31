package com.aurora.observation.record;

import com.aurora.observation.dto.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcRunRecordStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @TempDir Path directory;
    private final Map<String, JdbcTemplate> mysqlDatabases = new HashMap<>();
    private final List<String> mysqlSchemas = new java.util.ArrayList<>();

    @Test
    void startupProbeReadsPreviousDeploymentAndCleansOnlyExpiredEvidence() {
        String url = "jdbc:h2:file:" + directory.resolve("probe") + ";DB_CLOSE_ON_EXIT=FALSE";
        JdbcTemplate jdbc = jdbc(url);
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations()).load().migrate();
        var first = store(jdbc, NOW);
        String normal = first.begin("PAGE_FACTS", 2964574L);
        first.recordFacts(normal, facts()); first.finish(normal, "COMPLETED");
        var original = first.verifyPersistence();
        assertNull(original.previousRunId());
        var restarted = store(jdbc(url), NOW.plusSeconds(60));
        var next = restarted.verifyPersistence();
        assertEquals(original.newRunId(), next.previousRunId());
        assertEquals(1, restarted.find(original.newRunId()).orElseThrow().toolCalls().size());
        assertEquals(3, restarted.find(normal).orElseThrow().sources().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_run WHERE kind = 'RETENTION_CHECK'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assistant_tool_call WHERE tool_name = 'retention_probe'", Integer.class));
    }

    @Test
    void recordReadbackWorksWithSingleConnectionPool() {
        JdbcTemplate jdbc = jdbc("jdbc:h2:mem:singlepool;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations()).load().migrate();
        try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
            pool.setDataSource(jdbc.getDataSource()); pool.setMaximumPoolSize(1); pool.setConnectionTimeout(500);
            var records = store(new JdbcTemplate(pool), NOW);
            String id = records.begin("PAGE_FACTS", 2964574L);
            records.recordFacts(id, facts()); records.finish(id, "COMPLETED");
            assertEquals(3, records.find(id).orElseThrow().sources().size());
        }
    }

    @Test
    void publicHistorySurvivesRestartDeduplicatesAndExpires() {
        String url = "jdbc:h2:file:" + directory.resolve("history") + ";DB_CLOSE_ON_EXIT=FALSE";
        JdbcTemplate jdbc=jdbc(url);
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations()).load().migrate();
        var records=store(jdbc,NOW);
        var snapshot=new com.aurora.observation.monitoring.ObservationSnapshot(2964574L,"Dublin","Europe/Dublin",NOW,
                12,"CURRENT",NOW.minusSeconds(60),NOW.plusSeconds(1800),42.5,true,"MEDIUM",NOW,NOW.plusSeconds(1800),"sky-conditions-v2","CURRENT",NOW);
        records.saveHistory(snapshot); records.saveHistory(snapshot);
        var loaded=store(jdbc(url),NOW).history(2964574L);
        assertEquals(1,loaded.size()); assertEquals(snapshot,loaded.getFirst());
        assertTrue(records.history(2965768L).isEmpty());
        assertTrue(store(jdbc,NOW.plusSeconds(8*86400)).history(2964574L).isEmpty());
        store(jdbc,NOW.plusSeconds(8*86400)).purgeExpired();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM observation_history",Integer.class));
    }

    @Test
    void migratesEmptyDatabaseAndReadsMinimalEvidenceAfterRestart() {
        String url = "jdbc:h2:file:" + directory.resolve("records") + ";DB_CLOSE_ON_EXIT=FALSE";
        JdbcTemplate first = jdbc(url);
        assertEquals(4, Flyway.configure().dataSource(first.getDataSource()).locations(migrations()).load().migrate().migrationsExecuted);
        JdbcRunRecordStore records = store(first, NOW);
        String id = records.begin("PAGE_FACTS", 2964574L);
        records.recordFacts(id, facts());
        records.finish(id, "COMPLETED");

        JdbcTemplate restarted = jdbc(url);
        assertEquals(0, Flyway.configure().dataSource(restarted.getDataSource()).locations(migrations()).load().migrate().migrationsExecuted);
        assertEquals("COMPLETED", restarted.queryForObject(
                "SELECT result_status FROM evaluation_run WHERE run_id = ?", String.class, id));
        assertEquals(1, restarted.queryForObject(
                "SELECT COUNT(*) FROM evaluation_night WHERE run_id = ?", Integer.class, id));
        String cloud = restarted.queryForObject("SELECT evidence_json FROM evaluation_source "
                + "WHERE run_id = ? AND source_key = 'cloud'", String.class, id);
        assertTrue(cloud.contains("42.5"));
        assertFalse(cloud.contains("53.333"));

        records.recordTool(id, 1, "get_local_night_facts", "OK", "{\"locationId\":2964574}");
        assertEquals("get_local_night_facts", restarted.queryForObject(
                "SELECT tool_name FROM assistant_tool_call WHERE run_id = ?", String.class, id));
        RunRecord view = store(restarted, NOW).find(id).orElseThrow();
        assertEquals(NOW, view.createdAtUtc());
        assertEquals(NOW, view.completedAtUtc());
        assertEquals(JdbcRunRecordStore.RULE_VERSION, view.ruleVersion());
        assertEquals("NOT_VALIDATED", view.ruleStatus());
        assertEquals("OVERLAPS", view.coverageStatus());
        assertEquals("RULES_NOT_VALIDATED", view.nights().getFirst().reasonCode());
        assertEquals(3, view.sources().size());
        assertEquals(1, view.toolCalls().size());
        assertFalse(view.toString().contains("53.33306"));
    }

    @Test
    void productionConfigurationSelectsCompatibleMigrations() throws Exception {
        DriverManagerDataSource dataSource = (DriverManagerDataSource) jdbc(
                "jdbc:h2:mem:configuredrecords;DB_CLOSE_DELAY=-1").getDataSource();
        RunRecordStore records = new RunRecordConfiguration().jdbcRunRecordStore(
                dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword(), 7,
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        String id = records.begin("PAGE_FACTS", 2964574L);
        records.finish(id, "COMPLETED");
        assertEquals(NOW, records.find(id).orElseThrow().createdAtUtc());
        ((JdbcRunRecordStore) records).close();
    }

    @Test
    void upgradesExistingRecordsWithoutLosingEvidence() {
        JdbcTemplate jdbc = jdbc("jdbc:h2:mem:upgradefacts;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations()).target("1").load().migrate();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO evaluation_run(run_id, kind, created_at_utc, result_status, location_ref, rule_version, rule_status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, "PAGE_FACTS", java.sql.Timestamp.from(NOW), "RUNNING", 2964574L, JdbcRunRecordStore.RULE_VERSION, "NOT_VALIDATED");
        jdbc.update("INSERT INTO evaluation_night(run_id, local_date, window_start_utc, window_end_utc, level, reason_code) VALUES (?, ?, ?, ?, ?, ?)",
                id, java.sql.Date.valueOf("2026-10-01"), java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW.plusSeconds(86400)), "INSUFFICIENT_DATA", "RULES_NOT_VALIDATED");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations()).load().migrate();
        RunRecord view = store(jdbc, NOW).find(id).orElseThrow();
        assertEquals(1, view.nights().size());
        assertEquals(2964574L, view.nights().getFirst().locationId());
        assertEquals(1, view.nights().getFirst().snapshot());
        store(jdbc, NOW).recordFacts(id, facts());
        assertEquals(2, store(jdbc, NOW).find(id).orElseThrow().nights().size());
    }

    @Test
    void retainsMultipleLocationsAndRepeatedQueries() {
        JdbcTemplate jdbc = jdbc("jdbc:h2:mem:multifacts;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations()).load().migrate();
        JdbcRunRecordStore records = store(jdbc, NOW);
        String id = records.begin("ASSISTANT", null);
        records.recordFacts(id, facts(2964574));
        records.recordFacts(id, facts(2965140));
        records.recordFacts(id, facts(2964574));
        RunRecord view = records.find(id).orElseThrow();
        assertEquals(3, view.nights().size());
        assertEquals(9, view.sources().size());
        assertEquals(List.of(2964574L, 2965140L, 2964574L), view.nights().stream().map(RunRecord.Night::locationId).toList());
        assertEquals(List.of(1, 2, 3), view.nights().stream().map(RunRecord.Night::snapshot).toList());
    }

    @Test
    void deletesExpiredRunAndChildRecordsTogether() {
        JdbcTemplate jdbc = jdbc("jdbc:h2:mem:runretention;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations()).load().migrate();
        JdbcRunRecordStore initial = store(jdbc, NOW);
        String old = initial.begin("ASSISTANT", null);
        initial.recordFacts(old, facts());
        initial.recordTool(old, 1, "get_local_night_facts", "OK", "{}");
        initial.finish(old, "COMPLETED");
        String retained = store(jdbc, NOW.plusSeconds(2 * 86400L)).begin("PAGE_FACTS", 2964574L);
        store(jdbc, NOW.plusSeconds(2 * 86400L)).recordFacts(retained, facts());

        store(jdbc, NOW.plusSeconds(8 * 86400L)).purgeExpired();

        for (String table : List.of("evaluation_run", "evaluation_night", "evaluation_source", "assistant_tool_call")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Integer.class, old));
        }
        assertTrue(initial.find(old).isEmpty());
        RunRecord remaining = initial.find(retained).orElseThrow();
        assertEquals(1, remaining.nights().size());
        assertEquals(3, remaining.sources().size());
    }

    @Test
    void databaseFailureIsAnExplicitErrorRatherThanAnObservationGrade() {
        JdbcTemplate jdbc = jdbc("jdbc:h2:tcp://127.0.0.1:1/missing");
        assertThrows(RunRecordUnavailableException.class, () -> store(jdbc, NOW).begin("PAGE_FACTS", 1L));
    }

    private String migrations() {
        return System.getenv("TEST_MYSQL_URL") == null ? "classpath:db/migration" : "classpath:db/mysql";
    }

    private JdbcTemplate jdbc(String url) {
        String mysqlUrl = System.getenv("TEST_MYSQL_URL");
        if (mysqlUrl == null || url.startsWith("jdbc:h2:tcp:")) {
            return new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        }
        return mysqlDatabases.computeIfAbsent(url, ignored -> {
            String schema = "run_test_" + UUID.randomUUID().toString().replace("-", "");
            JdbcTemplate admin = mysqlAdmin(mysqlUrl);
            admin.execute("CREATE DATABASE " + schema);
            mysqlSchemas.add(schema);
            String scopedUrl = mysqlUrl.replaceFirst("/[^/?]+(?=\\?|$)", "/" + schema);
            return new JdbcTemplate(new DriverManagerDataSource(scopedUrl,
                    System.getenv("TEST_MYSQL_USER"), System.getenv("TEST_MYSQL_PASSWORD")));
        });
    }

    private JdbcTemplate mysqlAdmin(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url,
                System.getenv("TEST_MYSQL_USER"), System.getenv("TEST_MYSQL_PASSWORD")));
    }

    @AfterEach
    void removeDisposableTestSchemas() {
        String url = System.getenv("TEST_MYSQL_URL");
        if (url != null) {
            for (String schema : mysqlSchemas) mysqlAdmin(url).execute("DROP DATABASE " + schema);
        }
    }

    private JdbcRunRecordStore store(JdbcTemplate jdbc, Instant now) {
        return new JdbcRunRecordStore(jdbc, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), 7);
    }

    private ObservationFactsResponse facts() {
        return facts(2964574);
    }

    private ObservationFactsResponse facts(long locationId) {
        Location location = new Location(locationId, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        NightOutlook night = new NightOutlook(LocalDate.of(2026, 10, 1), "+01:00", NOW,
                NOW.plusSeconds(86400), OutlookLevel.INSUFFICIENT_DATA,
                OutlookReasonCode.RULES_NOT_VALIDATED, null);
        OutlookResponse outlook = new OutlookResponse(location, NOW, RuleStatus.NOT_VALIDATED, List.of(night));
        LocalAuroraActivityResponse aurora = new LocalAuroraActivityResponse(ForecastStatus.CURRENT,
                LocalAuroraActivityLevel.LOW, 12, -6.2, 53.3, NOW, NOW.plusSeconds(3600), NOW,
                "NOAA", "model-display-v1");
        SourceFact<LocalAuroraActivityResponse> auroraFact = new SourceFact<>(FactFetchStatus.CURRENT,
                FactTimeScope.SHORT_RANGE, NOW, NOW, NOW.plusSeconds(3600), NOW, NOW.plusSeconds(3600),
                "NOAA", "https://example.test", null, aurora);
        WeatherForecastResponse weather = new WeatherForecastResponse(NOW, NOW.plusSeconds(3600), "MET",
                53.33306, -6.24889, List.of(new WeatherCloudPoint(NOW, 42.5)));
        SourceFact<WeatherForecastResponse> cloudFact = new SourceFact<>(FactFetchStatus.CURRENT,
                FactTimeScope.TONIGHT, NOW, null, null, NOW, NOW.plusSeconds(3600),
                "MET", "https://example.test", null, weather);
        SourceFact<List<SolarNightFact>> darkness = new SourceFact<>(FactFetchStatus.CURRENT,
                FactTimeScope.THREE_LOCAL_NIGHTS, NOW, null, null, NOW, NOW.plusSeconds(86400),
                "SPA", "https://example.test", null, List.of());
        return new ObservationFactsResponse(NOW, outlook, auroraFact, cloudFact, darkness,
                new ForecastCoverage(ForecastCoverage.Status.OVERLAPS, FactTimeScope.SHORT_RANGE,
                        FactTimeScope.TONIGHT, NOW, NOW.plusSeconds(3600), 1), FactFetchStatus.CURRENT);
    }
}
