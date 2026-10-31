package com.aurora.observation.push;

import com.aurora.observation.dto.KpIndexRecord;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.KpIndexType;
import com.aurora.observation.service.KpIndexService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StormPushServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T22:00:00Z");

    private static KpIndexRecord record(String start, double kp, KpIndexType type) {
        return new KpIndexRecord(Instant.parse(start), kp, type, null, null, null);
    }

    @Test
    void usesThePagesRuleForCurrentAndForecastAlerts() {
        var alerts = StormPushService.alerts(List.of(
                record("2026-10-31T21:00:00Z", 4.67, KpIndexType.ESTIMATED),
                record("2026-10-31T21:00:00Z", 5.33, KpIndexType.OBSERVED),
                record("2026-11-01T00:00:00Z", 4.0, KpIndexType.PREDICTED),
                record("2026-11-01T03:00:00Z", 6.0, KpIndexType.PREDICTED),
                record("2026-11-01T06:00:00Z", 7.0, KpIndexType.PREDICTED),
                record("2026-11-05T00:00:00Z", 8.0, KpIndexType.PREDICTED)), NOW);

        assertEquals(List.of("current:2026-10-31T21:00:00Z:5", "forecast:2026-11-01T03:00:00Z:6"),
                alerts.stream().map(StormPushService.Alert::key).toList());
        assertTrue(StormPushService.alerts(List.of(record("2026-10-31T21:00:00Z", 4.9, KpIndexType.OBSERVED)), NOW).isEmpty());
    }

    @Test
    void pushesEachAlertOnceAndRemovesExpiredSubscriptions() {
        var kp = mock(KpIndexService.class);
        when(kp.latest()).thenReturn(new KpIndexResponse(NOW, "NOAA", List.of(record("2026-10-31T21:00:00Z", 5.33, KpIndexType.OBSERVED))));
        var store = mock(PushSubscriptionStore.class);
        var active = new PushSubscription("https://fcm.googleapis.com/fcm/send/a", "k", "a", "zh");
        var expired = new PushSubscription("https://fcm.googleapis.com/fcm/send/b", "k", "a", "en");
        when(store.available()).thenReturn(true);
        when(store.all()).thenReturn(List.of(active, expired));
        when(store.markAlertSent("current:2026-10-31T21:00:00Z:5")).thenReturn(true, false);
        var sender = mock(WebPushSender.class);
        when(sender.enabled()).thenReturn(true);
        when(sender.send(eq(active), any(), any())).thenReturn(WebPushSender.Result.SENT);
        when(sender.send(eq(expired), any(), any())).thenReturn(WebPushSender.Result.GONE);
        var service = new StormPushService(kp, store, sender, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.checkAndPush();
        service.checkAndPush();

        verify(sender, times(1)).send(eq(active), any(), any());
        verify(store).delete(expired.endpoint());
        String chinese = new String(service.payload(StormPushService.alerts(kp.latest().records(), NOW).getFirst(), true), StandardCharsets.UTF_8);
        assertTrue(chinese.contains("当前时段地磁活动增强 · Kp 5.33"));
        assertTrue(chinese.contains("\"tag\":\"current:2026-10-31T21:00:00Z:5\""));
    }

    @Test
    void doesNothingWithoutKeys() {
        var sender = mock(WebPushSender.class);
        var kp = mock(KpIndexService.class);
        new StormPushService(kp, mock(PushSubscriptionStore.class), sender, new ObjectMapper(), Clock.systemUTC()).checkAndPush();
        verifyNoInteractions(kp);
    }
}
