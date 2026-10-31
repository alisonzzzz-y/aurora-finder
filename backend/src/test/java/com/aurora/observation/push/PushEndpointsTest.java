package com.aurora.observation.push;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushEndpointsTest {
    @Test
    void acceptsBrowserPushServicesOnly() {
        assertTrue(PushEndpoints.allowed("https://fcm.googleapis.com/fcm/send/abc"));
        assertTrue(PushEndpoints.allowed("https://updates.push.services.mozilla.com/wpush/v2/abc"));
        assertTrue(PushEndpoints.allowed("https://web.push.apple.com/QK-abc"));
        assertTrue(PushEndpoints.allowed("https://wns2-par02p.notify.windows.com/w/?token=abc"));
        // The server posts to the endpoint, so anything else could reach arbitrary or internal hosts.
        assertFalse(PushEndpoints.allowed("http://fcm.googleapis.com/fcm/send/abc"));
        assertFalse(PushEndpoints.allowed("https://evilfcm.googleapis.com.example.com/x"));
        assertFalse(PushEndpoints.allowed("https://notfcm.googleapis.com.attacker.test/x"));
        assertFalse(PushEndpoints.allowed("https://169.254.169.254/latest/meta-data"));
        assertFalse(PushEndpoints.allowed("https://localhost/x"));
        assertFalse(PushEndpoints.allowed("https://user@fcm.googleapis.com/x"));
        assertFalse(PushEndpoints.allowed("https://fcm.googleapis.com:8443/x"));
        assertFalse(PushEndpoints.allowed("https://fcm.googleapis.com/" + "a".repeat(1100)));
    }
}
