package com.synapse.ai;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.synapse.ai.ChatService.Source;
import com.synapse.ai.ChatService.Turn;
import com.synapse.ai.ChatService.ViewContext;
import com.synapse.common.ApiException;
import com.synapse.project.ProjectRepository;
import com.synapse.project.ProjectRepository.ProjectRow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.ObjectMapper;

/**
 * Chat about a project. The response is newline-delimited JSON events so the UI can show the
 * answer while it is being written: {"type":"sources"}, {"type":"waiting"}, {"type":"token"},
 * then {"type":"done"} or {"type":"error"}.
 */
@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    public record ChatRequest(
        @NotBlank(message = "Ask a question") @Size(max = 2_000) String message,
        @Size(max = 20) List<Turn> history,
        ViewContext context) {
    }

    private final ChatService chat;
    private final ProjectRepository projects;
    private final ObjectMapper json = new ObjectMapper();

    public ChatController(ChatService chat, ProjectRepository projects) {
        this.chat = chat;
        this.projects = projects;
    }

    @PostMapping(value = "/api/projects/{projectId}/chat", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> chat(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId,
                                                      @Valid @RequestBody ChatRequest request) {
        ProjectRow project = projects.findForOwner(projectId, Long.parseLong(jwt.getSubject()))
            .orElseThrow(() -> ApiException.notFound("Project"));
        if (!"COMPLETED".equals(project.status()) && !"GENERATING_DOCS".equals(project.status())) {
            throw ApiException.badRequest("The assistant is available once the knowledge graph has been built.");
        }
        List<Turn> history = request.history() == null ? List.of() : request.history();

        StreamingResponseBody body = out -> {
            if (!chat.aiReady()) {
                send(out, Map.of("type", "error", "message",
                    "The local AI model is not running. Start Ollama (ollama serve) and try again."));
                return;
            }
            try {
                chat.answer(project, request.message().trim(), history, request.context(), new ChatService.Listener() {
                    @Override
                    public void sources(List<Source> sources) {
                        send(out, Map.of("type", "sources", "sources", sources));
                    }

                    @Override
                    public void callSites(List<ChatService.CallSiteGroup> groups) {
                        send(out, Map.of("type", "callsites", "groups", groups));
                    }

                    @Override
                    public void waiting() {
                        send(out, Map.of("type", "waiting", "message", "The AI is busy writing documentation for another analysis. Your question is queued."));
                    }

                    @Override
                    public void token(String text) {
                        send(out, Map.of("type", "token", "text", text));
                    }
                });
                send(out, Map.of("type", "done"));
            } catch (ClientGoneException e) {
                log.debug("Chat client disconnected");
            } catch (RuntimeException e) {
                log.warn("Chat failed for project {}", projectId, e);
                send(out, Map.of("type", "error", "message", "The assistant could not answer. Please try again."));
            }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-ndjson")).body(body);
    }

    private void send(OutputStream out, Map<String, Object> event) {
        try {
            out.write((json.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new ClientGoneException();
        }
    }

    /** The browser closed the request (e.g. the user pressed Stop); generation ends with it. */
    private static final class ClientGoneException extends RuntimeException {
    }
}
