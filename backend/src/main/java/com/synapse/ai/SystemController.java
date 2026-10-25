package com.synapse.ai;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the frontend show whether the local AI model is ready before starting an analysis. */
@RestController
public class SystemController {

    private final LocalLlm llm;

    public SystemController(LocalLlm llm) {
        this.llm = llm;
    }

    @GetMapping("/api/system/status")
    public Map<String, Object> status() {
        LocalLlm.Status status = llm.status();
        return Map.of(
            "backend", "up",
            "ai", Map.of("reachable", status.reachable(), "modelInstalled", status.modelInstalled(), "model", llm.model()));
    }
}
