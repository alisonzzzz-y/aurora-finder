package com.aurora.observation.push;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushControllerTest {
    private final PushSubscriptionStore store = mock(PushSubscriptionStore.class);
    private final WebPushSender sender = mock(WebPushSender.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PushController(store, sender)).build();

    private String body(String endpoint) throws Exception {
        String key = VapidSignerTest.keys()[0];
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + key + "\",\"auth\":\"BTBZMqHH6r4Tts7J_aSIgg\"},\"language\":\"zh\"}";
    }

    @Test
    void reportsDisabledWithoutKeysOrStorage() throws Exception {
        when(store.available()).thenReturn(false);
        mvc.perform(get("/api/v1/push/config")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/push/subscriptions").contentType("application/json").content(body("https://fcm.googleapis.com/fcm/send/a")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void storesBrowserSubscriptionsAndRejectsOtherHosts() throws Exception {
        when(store.available()).thenReturn(true);
        when(sender.enabled()).thenReturn(true);
        when(sender.publicKey()).thenReturn("public");
        mvc.perform(get("/api/v1/push/config")).andExpect(jsonPath("$.publicKey").value("public"));
        mvc.perform(post("/api/v1/push/subscriptions").contentType("application/json").content(body("https://fcm.googleapis.com/fcm/send/a")))
                .andExpect(status().isNoContent());
        verify(store).save(any());
        mvc.perform(post("/api/v1/push/subscriptions").contentType("application/json").content(body("https://internal.example/hook")))
                .andExpect(status().isBadRequest());
        verify(store, times(1)).save(any());
        when(store.count()).thenReturn(PushController.MAX_SUBSCRIPTIONS);
        mvc.perform(post("/api/v1/push/subscriptions").contentType("application/json").content(body("https://fcm.googleapis.com/fcm/send/b")))
                .andExpect(status().isServiceUnavailable());
    }
}
