package io.github.dreadvoice.incant.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.dreadvoice.incant.agent.AgentOrchestrator;
import io.github.dreadvoice.incant.agent.SkillTools;
import io.github.dreadvoice.incant.agent.SystemPromptBuilder;
import io.github.dreadvoice.incant.agent.ToolDispatcher;
import io.github.dreadvoice.incant.provider.ChatModelResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String BASE_PROMPT = "You are Incant, a local runtime that runs published skills on the "
            + "model provider the user configured. Answer the user directly and use a skill only when it applies.";

    private final ChatModelResolver models;
    private final ToolDispatcher dispatcher;
    private final SystemPromptBuilder promptBuilder;
    private final int maxIterations;

    public ChatController(ChatModelResolver models, ToolDispatcher dispatcher, SystemPromptBuilder promptBuilder,
            @Value("${incant.max-iterations}") int maxIterations) {
        this.models = models;
        this.dispatcher = dispatcher;
        this.promptBuilder = promptBuilder;
        this.maxIterations = maxIterations;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@Valid @RequestBody ChatMessage request) {
        ChatModelResolver.Resolved resolved = models.resolve(request.provider(), request.model());

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                resolved.chatModel(), dispatcher, SkillTools.all(), maxIterations);
        AgentOrchestrator.Result result = orchestrator.run(
                promptBuilder.build(BASE_PROMPT, false, false), request.message());

        return new ChatReply(result.text(), resolved.provider(), resolved.model(), result.telemetry());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    public record ChatMessage(@NotBlank String message, String provider, String model) {
    }

    public record ChatReply(String reply, String provider, String model, AgentOrchestrator.Telemetry telemetry) {
    }
}
