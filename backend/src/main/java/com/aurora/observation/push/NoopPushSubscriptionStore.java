package com.aurora.observation.push;

import java.time.Instant;
import java.util.List;

/** Used when run-record storage is disabled: background push needs persistent subscriptions. */
public class NoopPushSubscriptionStore implements PushSubscriptionStore {
    @Override public boolean available() { return false; }
    @Override public int count() { return 0; }
    @Override public void save(PushSubscription subscription) { throw new IllegalStateException("Push storage is disabled"); }
    @Override public void delete(String endpoint) {}
    @Override public List<PushSubscription> all() { return List.of(); }
    @Override public boolean markAlertSent(String alertKey) { return false; }
    @Override public void purgeAlertsBefore(Instant cutoff) {}
}
