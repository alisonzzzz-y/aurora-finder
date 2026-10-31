package com.aurora.observation.push;

import java.util.List;

public interface PushSubscriptionStore {
    default boolean available() { return true; }

    int count();

    void save(PushSubscription subscription);

    void delete(String endpoint);

    List<PushSubscription> all();

    /** Records an alert as broadcast. Returns false if it was already recorded. */
    boolean markAlertSent(String alertKey);

    void purgeAlertsBefore(java.time.Instant cutoff);
}
