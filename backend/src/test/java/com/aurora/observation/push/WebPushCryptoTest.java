package com.aurora.observation.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebPushCryptoTest {
    /** Example from RFC 8291, Appendix A. */
    @Test
    void matchesTheRfc8291Example() throws Exception {
        var receiver = WebPushCrypto.decodePublicKey(WebPushCrypto.base64Url(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"));
        var senderPrivate = WebPushCrypto.decodePrivateKey(WebPushCrypto.base64Url("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        var senderPublic = WebPushCrypto.decodePublicKey(WebPushCrypto.base64Url(
                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));

        byte[] message = WebPushCrypto.encrypt("When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                receiver, WebPushCrypto.base64Url("BTBZMqHH6r4Tts7J_aSIgg"), senderPrivate, senderPublic,
                WebPushCrypto.base64Url("DGv6ra1nlYgDCS1FRnbzlw"));

        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                WebPushCrypto.base64Url(message));
    }
}
