package com.aurora.observation.controller;

import com.aurora.observation.dto.AssistantChatRequest;
import com.aurora.observation.dto.AssistantChatResponse;
import com.aurora.observation.service.AssistantRequestLimiter;
import com.aurora.observation.service.AssistantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantService assistant;
    private final AssistantRequestLimiter limiter;

    public AssistantController(AssistantService assistant, AssistantRequestLimiter limiter) {
        this.assistant = assistant;
        this.limiter = limiter;
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request,
                                      HttpServletRequest servletRequest) {
        limiter.check(clientKey(servletRequest));
        return assistant.chat(request);
    }

    private String clientKey(HttpServletRequest request) {
        // Forwarded headers are untrusted unless the server has verified the proxy.
        return request.getRemoteAddr();
    }
}
