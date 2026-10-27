package com.aurora.observation.record;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableScheduling
public class RunRecordConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.run-record.enabled", havingValue = "true")
    RunRecordStore jdbcRunRecordStore(@Value("${app.run-record.jdbc-url:}") String url,
                                     @Value("${app.run-record.username:}") String username,
                                     @Value("${app.run-record.password:}") String password,
                                     @Value("${app.run-record.retention-days:7}") int retentionDays,
                                     Clock clock, ObjectMapper mapper) {
        if (url.isBlank() || retentionDays < 1) {
            throw new IllegalStateException("Run records require a JDBC URL and a positive retention period.");
        }
        var source = new HikariDataSource();
        source.setPoolName("run-records");
        source.setDataSource(new RetryingDriverDataSource(url, username, password));
        source.setMaximumPoolSize(3);
        source.setMinimumIdle(0);
        source.setConnectionTimeout(5000);
        source.setValidationTimeout(2000);
        source.setIdleTimeout(60000);
        source.setMaxLifetime(600000);
        try {
            Flyway.configure().dataSource(source).locations(url.startsWith("jdbc:mysql:") ? "classpath:db/mysql" : "classpath:db/migration").load().migrate();
            var store = new JdbcRunRecordStore(new JdbcTemplate(source), mapper, clock, retentionDays);
            var check = store.verifyPersistence();
            LoggerFactory.getLogger(RunRecordConfiguration.class).info(
                    "Run-record persistence check: previousRunId={}, newRunId={}, readback=true, retention=true",
                    check.previousRunId(), check.newRunId());
            return store;
        } catch (RuntimeException failure) {
            source.close();
            throw failure;
        }
    }

    @Bean
    @ConditionalOnMissingBean(RunRecordStore.class)
    RunRecordStore noopRunRecordStore() {
        return new NoopRunRecordStore();
    }
}
