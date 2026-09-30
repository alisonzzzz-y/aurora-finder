package com.aurora.observation.record;

import com.aurora.observation.dto.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.*;

class JdbcRunRecordStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @TempDir Path directory;

    @Test
    void migratesEmptyDatabaseAndReadsMinimalEvidenceAfterRestart() {
        String url = "jdbc:h2:file:" + directory.resolve("records") + ";DB_CLOSE_ON_EXIT=FALSE";
        JdbcTemplate first = jdbc(url);
        assertEquals(1, Flyway.configure().dataSource(first.getDataSource()).load().migrate().migrationsExecuted);
        JdbcRunRecordStore records = store(first, NOW);
        String id = records.begin("PAGE_FACTS", 2964574L);
        records.recordFacts(id, facts());
        records.finish(id, "COMPLETED");

        JdbcTemplate restarted = jdbc(url);
        assertEquals(0, Flyway.configure().dataSource(restarted.getDataSource()).load().migrate().migrationsExecuted);
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
        assertEquals(JdbcRunRecordStore.RULE_VERSION, view.ruleVersion());
        assertEquals("NOT_VALIDATED", view.ruleStatus());
        assertEquals("OVERLAPS", view.coverageStatus());
        assertEquals("RULES_NOT_VALIDATED", view.nights().getFirst().reasonCode());
        assertEquals(3, view.sources().size());
        assertEquals(1, view.toolCalls().size());
        assertFalse(view.toString().contains("53.33306"));
    }

    @Test
    void deletesExpiredRunAndChildRecordsTogether() {
        JdbcTemplate jdbc = jdbc("jdbc:h2:mem:runretention;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        String old = store(jdbc, NOW).begin("ASSISTANT", null);
        store(jdbc, NOW.plusSeconds(8 * 86400L)).begin("ASSISTANT", null);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_run WHERE run_id = ?", Integer.class, old));
        assertTrue(store(jdbc, NOW.plusSeconds(8 * 86400L)).find(old).isEmpty());
    }

    @Test
    void databaseFailureIsAnExplicitErrorRatherThanAnObservationGrade() {
        JdbcTemplate jdbc = jdbc("jdbc:h2:tcp://127.0.0.1:1/missing");
        assertThrows(RunRecordUnavailableException.class, () -> store(jdbc, NOW).begin("PAGE_FACTS", 1L));
    }

    private JdbcTemplate jdbc(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    }

    private JdbcRunRecordStore store(JdbcTemplate jdbc, Instant now) {
        return new JdbcRunRecordStore(jdbc, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), 7);
    }

    private ObservationFactsResponse facts() {
        Location location = new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland",
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
