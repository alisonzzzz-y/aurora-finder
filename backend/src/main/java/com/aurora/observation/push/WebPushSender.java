package com.aurora.observation.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;

/** Sends encrypted Web Push messages when VAPID keys are configured. */
@Component
public class WebPushSender {
    public enum Result { SENT, GONE, FAILED }

    private final HttpClient client;
    private final Clock clock;
    private final VapidSigner signer;

    public WebPushSender(HttpClient client, Clock clock,
                         @Value("${app.push.vapid-public-key:}") String publicKey,
                         @Value("${app.push.vapid-private-key:}") String privateKey,
                         @Value("${app.push.subject:}") String subject) throws GeneralSecurityException {
        this.client = client;
        this.clock = clock;
        boolean hasPublic = !publicKey.isBlank(), hasPrivate = !privateKey.isBlank();
        if (hasPublic != hasPrivate) throw new IllegalStateException("Set both VAPID keys, or neither.");
        this.signer = hasPublic ? new VapidSigner(publicKey, privateKey, subject) : null;
    }

    public boolean enabled() { return signer != null; }

    public String publicKey() { return signer == null ? null : signer.publicKey(); }

    public Result send(PushSubscription subscription, byte[] payload, Duration ttl) {
        if (signer == null) throw new IllegalStateException("Web Push is not configured");
        if (!PushEndpoints.allowed(subscription.endpoint())) return Result.GONE;
        try {
            URI endpoint = URI.create(subscription.endpoint());
            byte[] body = WebPushCrypto.encrypt(payload,
                    WebPushCrypto.decodePublicKey(WebPushCrypto.base64Url(subscription.p256dh())),
                    WebPushCrypto.base64Url(subscription.auth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                    .header("Authorization", signer.authorization(endpoint, clock.instant()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", Long.toString(ttl.toSeconds()))
                    .header("Urgency", "high")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) return Result.GONE;
            return status >= 200 && status < 300 ? Result.SENT : Result.FAILED;
        } catch (GeneralSecurityException | IllegalArgumentException invalidSubscription) {
            return Result.GONE;
        } catch (IOException unavailable) {
            return Result.FAILED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }
}
