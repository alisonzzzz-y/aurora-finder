package com.aurora.observation.push;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * The server sends requests to subscription endpoints, so only HTTPS endpoints of the browser push services
 * are accepted. Any other URL could make the server send requests to an arbitrary host.
 */
final class PushEndpoints {
    private static final List<String> PUSH_SERVICE_HOSTS = List.of(
            "fcm.googleapis.com",          // Chrome and other Chromium browsers
            "push.services.mozilla.com",   // Firefox
            "push.apple.com",              // Safari
            "notify.windows.com");         // Edge on Windows

    private PushEndpoints() {}

    static boolean allowed(String endpoint) {
        if (endpoint == null || endpoint.length() > 1024) return false;
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String normalized = host.toLowerCase(Locale.ROOT);
            return PUSH_SERVICE_HOSTS.stream().anyMatch(allowed -> normalized.equals(allowed) || normalized.endsWith("." + allowed));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
