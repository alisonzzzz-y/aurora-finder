package com.aurora.observation.push;

import com.aurora.observation.dto.KpIndexRecord;
import com.aurora.observation.dto.KpIndexType;
import com.aurora.observation.service.KpIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pushes the same strong-activity alerts as the page (Kp 5 or more now, or forecast within 72 hours) to
 * browsers that subscribed, even when the page is closed. Each period and level is pushed once.
 */
@Service
public class StormPushService {
    private static final Logger log = LoggerFactory.getLogger(StormPushService.class);
    private static final Duration PERIOD = Duration.ofHours(3);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_ZH = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA).withZone(ZoneOffset.UTC);

    public record Alert(String key, String kind, double kp, Instant start, Instant end) {}

    private final KpIndexService kpIndex;
    private final PushSubscriptionStore store;
    private final WebPushSender sender;
    private final ObjectMapper mapper;
    private final Clock clock;

    public StormPushService(KpIndexService kpIndex, PushSubscriptionStore store, WebPushSender sender,
                            ObjectMapper mapper, Clock clock) {
        this.kpIndex = kpIndex;
        this.store = store;
        this.sender = sender;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.push.initial-delay-ms:120000}", fixedDelayString = "${app.push.interval-ms:300000}")
    public void checkAndPush() {
        if (!sender.enabled() || !store.available()) return;
        try {
            Instant now = clock.instant();
            store.purgeAlertsBefore(now.minus(Duration.ofDays(7)));
            for (Alert alert : alerts(kpIndex.latest().records(), now)) {
                if (store.markAlertSent(alert.key())) broadcast(alert);
            }
        } catch (RuntimeException failure) {
            log.warn("Strong-activity push check failed: {}", failure.getClass().getSimpleName());
        }
    }

    /** Mirrors the page's alert rule in stormAlerts.ts so the page and push notifications agree. */
    static List<Alert> alerts(List<KpIndexRecord> records, Instant now) {
        List<KpIndexRecord> valid = records.stream()
                .filter(r -> r.periodStart() != null && Double.isFinite(r.kp()) && r.kp() >= 0 && r.kp() <= 9).toList();
        var current = valid.stream()
                .filter(r -> r.type() != KpIndexType.PREDICTED && !r.periodStart().isAfter(now) && r.periodStart().plus(PERIOD).isAfter(now))
                .max(Comparator.comparing(KpIndexRecord::periodStart).thenComparing(r -> r.type() == KpIndexType.OBSERVED));
        var forecast = valid.stream()
                .filter(r -> r.type() == KpIndexType.PREDICTED && r.kp() >= 5 && r.periodStart().isAfter(now)
                        && !r.periodStart().isAfter(now.plus(Duration.ofHours(72))))
                .min(Comparator.comparing(KpIndexRecord::periodStart));
        List<Alert> alerts = new ArrayList<>();
        current.filter(r -> r.kp() >= 5).ifPresent(r -> alerts.add(alert("current", r)));
        forecast.ifPresent(r -> alerts.add(alert("forecast", r)));
        return alerts;
    }

    private static Alert alert(String kind, KpIndexRecord record) {
        return new Alert(kind + ":" + record.periodStart() + ":" + (int) Math.floor(record.kp()), kind, record.kp(),
                record.periodStart(), record.periodStart().plus(PERIOD));
    }

    private void broadcast(Alert alert) {
        int sent = 0, gone = 0, failed = 0;
        for (PushSubscription subscription : store.all()) {
            var result = sender.send(subscription, payload(alert, "zh".equals(subscription.language())),
                    Duration.between(clock.instant(), alert.end()).isNegative() ? Duration.ofMinutes(5) : Duration.between(clock.instant(), alert.end()));
            switch (result) {
                case SENT -> sent++;
                case GONE -> { gone++; store.delete(subscription.endpoint()); }
                case FAILED -> failed++;
            }
        }
        // Counts only: endpoints are capability URLs and are never logged.
        log.info("Strong-activity push {} delivered: sent={}, removed={}, failed={}", alert.key(), sent, gone, failed);
    }

    byte[] payload(Alert alert, boolean chinese) {
        String kp = String.format(Locale.ROOT, "%.2f", alert.kp());
        String body = chinese
                ? (alert.kind().equals("current") ? "当前时段地磁活动增强" : "未来强活动时段") + " · Kp " + kp
                    + "\n" + TIME_ZH.format(alert.start()) + "–" + TIME_ZH.format(alert.end()) + " UTC。全球活动，不代表当地一定可见。"
                : (alert.kind().equals("current") ? "Elevated activity in the current period" : "Upcoming strong activity") + " · Kp " + kp
                    + "\n" + TIME.format(alert.start()) + "–" + TIME.format(alert.end()) + " UTC. Global activity, not a local viewing guarantee.";
        return mapper.writeValueAsString(Map.of("title", "Aurora Finder", "body", body, "tag", alert.key(), "url", "/"))
                .getBytes(StandardCharsets.UTF_8);
    }
}
