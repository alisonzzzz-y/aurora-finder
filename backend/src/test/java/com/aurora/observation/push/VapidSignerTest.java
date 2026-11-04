package com.aurora.observation.push;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VapidSignerTest {
    static String[] keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        byte[] scalar = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
        byte[] fixed = new byte[32];
        int start = Math.max(0, scalar.length - 32);
        System.arraycopy(scalar, start, fixed, 32 - (scalar.length - start), scalar.length - start);
        return new String[]{WebPushCrypto.base64Url(WebPushCrypto.encodePublicKey((ECPublicKey) pair.getPublic())),
                WebPushCrypto.base64Url(fixed)};
    }

    @Test
    void signsAJwtForThePushServiceOrigin() throws Exception {
        String[] keys = keys();
        var signer = new VapidSigner(keys[0], keys[1], "mailto:ops@example.test");
        String header = signer.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc"), Instant.ofEpochSecond(1_000));

        assertTrue(header.startsWith("vapid t="));
        assertTrue(header.endsWith(", k=" + keys[0]));
        String[] token = header.substring("vapid t=".length(), header.indexOf(',')).split("\\.");
        String claims = new String(WebPushCrypto.base64Url(token[1]), StandardCharsets.UTF_8);
        assertEquals("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":44200,\"sub\":\"mailto:ops@example.test\"}", claims);
        assertTrue(signer.verify((token[0] + "." + token[1]).getBytes(StandardCharsets.US_ASCII), WebPushCrypto.base64Url(token[2])));
        assertEquals(64, WebPushCrypto.base64Url(token[2]).length);
    }

    @Test
    void rejectsKeysThatDoNotBelongTogetherAndInvalidSubjects() throws Exception {
        String[] first = keys(), second = keys();
        assertThrows(GeneralSecurityException.class, () -> new VapidSigner(first[0], second[1], "mailto:ops@example.test"));
        assertThrows(GeneralSecurityException.class, () -> new VapidSigner(first[0], first[1], "ops@example.test"));
        assertThrows(GeneralSecurityException.class, () -> new VapidSigner(first[0], WebPushCrypto.base64Url(Arrays.copyOf(new byte[]{1}, 31)), "mailto:a@b.c"));
    }
}
