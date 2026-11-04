package com.aurora.observation.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/** Web Push message encryption (RFC 8291, aes128gcm) and P-256 key encoding, using only JDK cryptography. */
public final class WebPushCrypto {
    static final int RECORD_SIZE = 4096;
    private static final ECParameterSpec P256 = p256();
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {}

    /** Encrypts a payload for one subscription with a fresh sender key and salt. */
    public static byte[] encrypt(byte[] plaintext, ECPublicKey receiver, byte[] authSecret) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        KeyPair sender = generator.generateKeyPair();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, receiver, authSecret, (ECPrivateKey) sender.getPrivate(), (ECPublicKey) sender.getPublic(), salt);
    }

    static byte[] encrypt(byte[] plaintext, ECPublicKey receiver, byte[] authSecret, ECPrivateKey senderPrivate,
                          ECPublicKey senderPublic, byte[] salt) throws GeneralSecurityException {
        if (plaintext.length > RECORD_SIZE - 17 - 86) throw new IllegalArgumentException("Push payload is too large");
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(senderPrivate);
        agreement.doPhase(receiver, true);
        byte[] sharedSecret = agreement.generateSecret();
        byte[] receiverBytes = encodePublicKey(receiver);
        byte[] senderBytes = encodePublicKey(senderPublic);

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), receiverBytes, senderBytes);
        byte[] ikm = hkdf(authSecret, sharedSecret, keyInfo, 32);
        byte[] contentKey = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        // A single record ends with the 0x02 padding delimiter.
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + senderBytes.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) senderBytes.length).put(senderBytes);
        return concat(header.array(), ciphertext);
    }

    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] okm = mac.doFinal(concat(info, new byte[]{1}));
        return Arrays.copyOf(okm, length);
    }

    /** Uncompressed P-256 point: 0x04 || X || Y. */
    public static byte[] encodePublicKey(ECPublicKey key) {
        byte[] result = new byte[65];
        result[0] = 4;
        copyUnsigned(key.getW().getAffineX(), result, 1);
        copyUnsigned(key.getW().getAffineY(), result, 33);
        return result;
    }

    public static ECPublicKey decodePublicKey(byte[] encoded) throws GeneralSecurityException {
        if (encoded.length != 65 || encoded[0] != 4) throw new GeneralSecurityException("Expected an uncompressed P-256 key");
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(encoded, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(encoded, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    public static ECPrivateKey decodePrivateKey(byte[] scalar) throws GeneralSecurityException {
        if (scalar.length != 32) throw new GeneralSecurityException("Expected a 32-byte P-256 private key");
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
    }

    public static byte[] base64Url(String value) {
        return Base64.getUrlDecoder().decode(value.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void copyUnsigned(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, target, offset + 32 - length, length);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) output.writeBytes(part);
        return output.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("P-256 is unavailable", error);
        }
    }
}
