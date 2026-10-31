package com.aurora.observation.push;

import com.aurora.observation.record.JdbcRunRecordStore;
import com.aurora.observation.record.RunRecordStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PushConfiguration {
    /** Subscriptions share the run-record database; without it, background alerts stay off. */
    @Bean
    PushSubscriptionStore pushSubscriptionStore(RunRecordStore records, Clock clock) {
        return records instanceof JdbcRunRecordStore jdbc
                ? new JdbcPushSubscriptionStore(jdbc.jdbcTemplate(), clock)
                : new NoopPushSubscriptionStore();
    }
}
