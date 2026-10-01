package com.aurora.observation.record;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
        DriverManagerDataSource source = new DriverManagerDataSource(url, username, password);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        return new JdbcRunRecordStore(new JdbcTemplate(source), mapper, clock, retentionDays);
    }

    @Bean
    @ConditionalOnMissingBean(RunRecordStore.class)
    RunRecordStore noopRunRecordStore() {
        return new NoopRunRecordStore();
    }
}
