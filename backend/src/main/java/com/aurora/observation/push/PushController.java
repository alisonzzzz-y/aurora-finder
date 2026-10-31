package com.aurora.observation.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/push")
public class PushController {
    // Bounds the stored subscriptions; each one is contacted for every alert.
    static final int MAX_SUBSCRIPTIONS = 5000;

    public record Config(boolean enabled, String publicKey) {}
    public record Keys(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 64) String auth) {}
    public record SubscribeRequest(@NotBlank @Size(max = 1024) String endpoint, @NotNull @Valid Keys keys,
                                   @Pattern(regexp = "en|zh") String language) {}
    public record UnsubscribeRequest(@NotBlank @Size(max = 1024) String endpoint) {}

    private final PushSubscriptionStore store;
    private final WebPushSender sender;

    public PushController(PushSubscriptionStore store, WebPushSender sender) {
        this.store = store;
        this.sender = sender;
    }

    @GetMapping("/config")
    public Config config() {
        boolean enabled = sender.enabled() && store.available();
        return new Config(enabled, enabled ? sender.publicKey() : null);
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request) {
        if (!sender.enabled() || !store.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Background alerts are not enabled.");
        if (!PushEndpoints.allowed(request.endpoint()) || !validKeys(request.keys())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This browser push subscription is not supported.");
        }
        if (store.count() >= MAX_SUBSCRIPTIONS) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Background alerts are full right now.");
        store.save(new PushSubscription(request.endpoint(), request.keys().p256dh(), request.keys().auth(),
                request.language() == null ? "en" : request.language()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        if (store.available()) store.delete(request.endpoint());
        return ResponseEntity.noContent().build();
    }

    private boolean validKeys(Keys keys) {
        try {
            WebPushCrypto.decodePublicKey(WebPushCrypto.base64Url(keys.p256dh()));
            return WebPushCrypto.base64Url(keys.auth()).length == 16;
        } catch (Exception invalid) {
            return false;
        }
    }
}
