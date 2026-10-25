package com.synapse.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The local Ollama model, accessed through Spring AI. Calls are serialised because a single laptop
 * GPU runs one generation at a time anyway.
 */
@Component
public class LocalLlm {

    private static final Logger log = LoggerFactory.getLogger(LocalLlm.class);

    private final ChatModel chatModel;
    private final ObjectMapper json = new ObjectMapper();
    private final Semaphore gpu = new Semaphore(1, true);
    private final String baseUrl;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public LocalLlm(ChatModel chatModel,
                    @Value("${spring.ai.ollama.base-url}") String baseUrl,
                    @Value("${spring.ai.ollama.chat.model}") String model) {
        this.chatModel = chatModel;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public String model() {
        return model;
    }

    public record Status(boolean reachable, boolean modelInstalled) {
        public boolean ready() {
            return reachable && modelInstalled;
        }
    }

    /** Checks that Ollama is running and the configured model has been pulled. */
    public Status status() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags")).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new Status(false, false);
            JsonNode models = json.readTree(response.body()).path("models");
            for (JsonNode m : models) {
                String name = m.path("name").asText("");
                if (name.equals(model) || name.equals(model + ":latest")) return new Status(true, true);
            }
            return new Status(true, false);
        } catch (Exception e) {
            return new Status(false, false);
        }
    }

    public String complete(String system, String user) {
        return call(system, user, false);
    }

    /** Asks for a JSON object and parses it, tolerating code fences or chatter around it. */
    public JsonNode completeJson(String system, String user) {
        String text = call(system, user, true);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Model did not return JSON");
        }
        return json.readTree(text.substring(start, end + 1));
    }

    private String call(String system, String user, boolean jsonMode) {
        try {
            gpu.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        }
        long started = System.currentTimeMillis();
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(system), new UserMessage(user)), options(jsonMode));
            String text = chatModel.call(prompt).getResult().getOutput().getText();
            return text == null ? "" : text.trim();
        } finally {
            gpu.release();
            log.debug("LLM call took {} ms", System.currentTimeMillis() - started);
        }
    }

    /**
     * Streams a chat completion token by token. Blocks until the answer is complete and returns it.
     * {@code onWaiting} runs if the GPU is busy (e.g. an analysis is writing docs) before generation starts.
     */
    public String stream(List<Message> messages, Runnable onWaiting, Consumer<String> onToken) {
        try {
            if (!gpu.tryAcquire()) {
                onWaiting.run();
                gpu.acquire();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        }
        StringBuilder full = new StringBuilder();
        try {
            for (ChatResponse chunk : chatModel.stream(new Prompt(messages, options(false))).toIterable()) {
                if (chunk.getResult() == null || chunk.getResult().getOutput() == null) continue;
                String text = chunk.getResult().getOutput().getText();
                if (text != null && !text.isEmpty()) {
                    full.append(text);
                    onToken.accept(text);
                }
            }
            return full.toString();
        } finally {
            gpu.release();
        }
    }

    // Options passed per call replace the configured defaults, so set them all here.
    private OllamaChatOptions options(boolean jsonMode) {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder();
        options.model(model);
        options.temperature(0.2);
        options.numCtx(8192);
        options.keepAlive("10m");
        if (jsonMode) options.format("json");
        return options.build();
    }
}
