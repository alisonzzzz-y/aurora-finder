package com.aurora.observation.record;

import com.aurora.observation.dto.LocalAuroraActivityResponse;
import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.dto.SourceFact;
import com.aurora.observation.dto.WeatherForecastResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionException;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

/** Stores source evidence without prompts, answer text, raw search strings, or precise coordinates. */
public class JdbcRunRecordStore implements RunRecordStore {
    public static final String RULE_VERSION = "abstain-unvalidated-v1";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int retentionDays;

    public JdbcRunRecordStore(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock, int retentionDays) {
        if (jdbc.getDataSource() == null || retentionDays < 1) throw new IllegalArgumentException("Invalid record store");
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.mapper = mapper;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Override
    public String begin(String kind, Long locationId) {
        String id = UUID.randomUUID().toString();
        try {
            transaction.executeWithoutResult(ignored -> {
                deleteExpired();
                jdbc.update("INSERT INTO evaluation_run(run_id, kind, created_at_utc, result_status, location_ref, "
                                + "rule_version, rule_status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        id, kind, timestamp(clock.instant()), "RUNNING", locationId, RULE_VERSION, "NOT_VALIDATED");
            });
            return id;
        } catch (DataAccessException | TransactionException error) {
            throw new RunRecordUnavailableException(error);
        }
    }

    @Override
    public void recordFacts(String runId, ObservationFactsResponse facts) {
        if (runId == null) return;
        try {
            transaction.executeWithoutResult(ignored -> {
                jdbc.update("UPDATE evaluation_run SET rule_status = ?, coverage_status = ? WHERE run_id = ?",
                        facts.outlook().ruleStatus().name(), facts.coverage().status().name(), runId);
                int snapshot = jdbc.queryForObject("SELECT COALESCE(MAX(snapshot_no), 0) + 1 FROM (SELECT snapshot_no FROM evaluation_source WHERE run_id = ? UNION ALL SELECT snapshot_no FROM evaluation_night WHERE run_id = ?) snapshots", Integer.class, runId, runId);
                long locationId = facts.outlook().location().id();
                for (var night : facts.outlook().nights()) {
                    jdbc.update("INSERT INTO evaluation_night(run_id, snapshot_no, location_ref, local_date, window_start_utc, window_end_utc, "
                                    + "level, reason_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            runId, snapshot, locationId, Date.valueOf(night.localDate()), timestamp(night.evaluationWindowStartUtc()),
                            timestamp(night.evaluationWindowEndUtc()), night.level().name(), night.reasonCode().name());
                }
                source(runId, snapshot, locationId, "aurora", facts.auroraActivity(), auroraEvidence(facts));
                source(runId, snapshot, locationId, "cloud", facts.cloudForecast(), cloudEvidence(facts));
                source(runId, snapshot, locationId, "darkness", facts.solarDarkness(),
                        mapper.writeValueAsString(facts.solarDarkness().data()));
            });
        } catch (DataAccessException | TransactionException error) {
            throw new RunRecordUnavailableException(error);
        }
    }

    @Override
    public void recordTool(String runId, int sequence, String name, String outcome, String evidenceJson) {
        if (runId == null) return;
        try {
            jdbc.update("INSERT INTO assistant_tool_call(run_id, sequence_no, tool_name, completed_at_utc, outcome, evidence_json) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", runId, sequence, name,
                    timestamp(clock.instant()), outcome, evidenceJson);
        } catch (DataAccessException error) {
            throw new RunRecordUnavailableException(error);
        }
    }

    @Override
    public void finish(String runId, String status) {
        if (runId == null) return;
        try {
            jdbc.update("UPDATE evaluation_run SET completed_at_utc = ?, result_status = ? WHERE run_id = ?",
                    timestamp(clock.instant()), status, runId);
        } catch (DataAccessException error) {
            throw new RunRecordUnavailableException(error);
        }
    }

    @Scheduled(fixedDelay = 86_400_000)
    public void purgeExpired() {
        try {
            deleteExpired();
        } catch (DataAccessException error) {
            throw new RunRecordUnavailableException(error);
        }
    }

    @Override
    public Optional<RunRecord> find(String runId) {
        try {
            var runs = jdbc.query("SELECT kind, created_at_utc, completed_at_utc, result_status, location_ref, "
                            + "rule_version, rule_status, coverage_status "
                            + "FROM evaluation_run WHERE run_id = ?", (row, number) -> new RunRecord(
                    runId, row.getString("kind"), instant(row, "created_at_utc"),
                    instant(row, "completed_at_utc"), row.getString("result_status"),
                    row.getObject("location_ref") == null ? null : row.getLong("location_ref"),
                    row.getString("rule_version"), row.getString("rule_status"),
                    row.getString("coverage_status"), nights(runId), sources(runId), toolCalls(runId)), runId);
            return runs.stream().findFirst();
        } catch (DataAccessException error) {
            throw new RunRecordUnavailableException(error);
        }
    }

    private List<RunRecord.Night> nights(String id) {
        return jdbc.query("SELECT snapshot_no, location_ref, local_date, window_start_utc, window_end_utc, level, reason_code "
                        + "FROM evaluation_night WHERE run_id = ? ORDER BY snapshot_no, local_date",
                (row, number) -> new RunRecord.Night(row.getInt("snapshot_no"), row.getObject("location_ref", Long.class), row.getDate("local_date").toLocalDate(),
                        instant(row, "window_start_utc"), instant(row, "window_end_utc"),
                        row.getString("level"), row.getString("reason_code")), id);
    }

    private List<RunRecord.Source> sources(String id) {
        return jdbc.query("SELECT snapshot_no, location_ref, source_key, fetch_status, time_scope, failure_code, retrieved_at_utc, "
                        + "observed_at_utc, forecast_at_utc, scope_start_utc, scope_end_utc, evidence_json "
                        + "FROM evaluation_source WHERE run_id = ? ORDER BY snapshot_no, source_key",
                (row, number) -> new RunRecord.Source(row.getInt("snapshot_no"), row.getObject("location_ref", Long.class), row.getString("source_key"), row.getString("fetch_status"),
                        row.getString("time_scope"), row.getString("failure_code"),
                        instant(row, "retrieved_at_utc"), instant(row, "observed_at_utc"),
                        instant(row, "forecast_at_utc"), instant(row, "scope_start_utc"),
                        instant(row, "scope_end_utc"), row.getString("evidence_json")), id);
    }

    private List<RunRecord.ToolCall> toolCalls(String id) {
        return jdbc.query("SELECT sequence_no, tool_name, completed_at_utc, outcome, evidence_json "
                        + "FROM assistant_tool_call WHERE run_id = ? ORDER BY sequence_no",
                (row, number) -> new RunRecord.ToolCall(row.getInt("sequence_no"), row.getString("tool_name"),
                        instant(row, "completed_at_utc"), row.getString("outcome"),
                        row.getString("evidence_json")), id);
    }

    private Instant instant(java.sql.ResultSet row, String field) throws java.sql.SQLException {
        Timestamp value = row.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }

    private void deleteExpired() {
        jdbc.update("DELETE FROM evaluation_run WHERE created_at_utc < ?",
                timestamp(clock.instant().minus(retentionDays, ChronoUnit.DAYS)));
    }

    private void source(String runId, int snapshot, long locationId, String key, SourceFact<?> fact, String evidence) {
        jdbc.update("INSERT INTO evaluation_source(run_id, snapshot_no, location_ref, source_key, fetch_status, time_scope, failure_code, "
                        + "retrieved_at_utc, observed_at_utc, forecast_at_utc, scope_start_utc, scope_end_utc, evidence_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId, snapshot, locationId, key, fact.status().name(), fact.timeScope().name(),
                fact.failureCode() == null ? null : fact.failureCode().name(),
                timestamp(fact.retrievedAtUtc()), timestamp(fact.sourceObservedAtUtc()),
                timestamp(fact.sourceForecastAtUtc()), timestamp(fact.scopeStartUtc()),
                timestamp(fact.scopeEndUtc()), evidence);
    }

    private String auroraEvidence(ObservationFactsResponse facts) {
        LocalAuroraActivityResponse data = facts.auroraActivity().data();
        if (data == null) return "{}";
        return mapper.writeValueAsString(new AuroraEvidence(data.status().name(),
                data.level() == null ? null : data.level().name(), data.modelValue(), facts.viewingConditions()));
    }

    private String cloudEvidence(ObservationFactsResponse facts) {
        WeatherForecastResponse data = facts.cloudForecast().data();
        return data == null ? "[]" : mapper.writeValueAsString(data.cloudForecast());
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private record AuroraEvidence(String status, String level, Integer modelValue, com.aurora.observation.dto.ViewingConditions viewingConditions) {}
}
