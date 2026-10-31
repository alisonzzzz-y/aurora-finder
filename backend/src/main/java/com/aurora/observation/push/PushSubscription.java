package com.aurora.observation.push;

/** A browser push subscription. {@code p256dh} and {@code auth} are base64url, as browsers report them. */
public record PushSubscription(String endpoint, String p256dh, String auth, String language) {}
