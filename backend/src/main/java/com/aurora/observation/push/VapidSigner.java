package com.aurora.observation.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;

/** VAPID authorization (RFC 8292): an ES256 JWT for the push service origin plus the server's public key. */
public final class VapidSigner {
    private final ECPublicKey publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;

    public VapidSigner(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
        this.publicKey = WebPushCrypto.decodePublicKey(WebPushCrypto.base64Url(publicKey));
        this.privateKey = WebPushCrypto.decodePrivateKey(WebPushCrypto.base64Url(privateKey));
        if (subject == null || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) {
            throw new GeneralSecurityException("The VAPID subject must be a mailto: or https: URL");
        }
        this.subject = subject;
        // Fail at startup if the configured keys do not belong together.
        byte[] probe = "vapid-key-check".getBytes(StandardCharsets.US_ASCII);
        if (!verify(probe, sign(probe))) throw new GeneralSecurityException("The VAPID public and private keys do not match");
    }

    public String publicKey() {
        return WebPushCrypto.base64Url(WebPushCrypto.encodePublicKey(publicKey));
    }

    public String authorization(URI endpoint, Instant now) throws GeneralSecurityException {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String claims = encode("{\"aud\":\"" + audience + "\",\"exp\":" + now.plus(Duration.ofHours(12)).getEpochSecond()
                + ",\"sub\":\"" + subject.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
        String unsigned = header + "." + claims;
        String token = unsigned + "." + WebPushCrypto.base64Url(sign(unsigned.getBytes(StandardCharsets.US_ASCII)));
        return "vapid t=" + token + ", k=" + publicKey();
    }

    private byte[] sign(byte[] data) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    boolean verify(byte[] data, byte[] raw) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(raw);
    }

    private static String encode(String json) {
        return WebPushCrypto.base64Url(json.getBytes(StandardCharsets.UTF_8));
    }
}
