package io.github.dreadvoice.incant.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.dreadvoice.incant.agent.AgentOrchestrator;
import io.github.dreadvoice.incant.agent.SystemPromptBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String BASE_PROMPT = "You are Incant, a local runtime that runs published skills on the "
            + "model provider the user configured. Answer the user directly and use a skill only when it applies.";

    private final AgentOrchestrator orchestrator;
    private final SystemPromptBuilder promptBuilder;

    public ChatController(AgentOrchestrator orchestrator, SystemPromptBuilder promptBuilder) {
        this.orchestrator = orchestrator;
        this.promptBuilder = promptBuilder;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@Valid @RequestBody ChatMessage request) {
        String systemPrompt = promptBuilder.build(BASE_PROMPT, false, false);
        AgentOrchestrator.Result result = orchestrator.run(systemPrompt, request.message());
        return new ChatReply(result.text(), result.iterations());
    }

    public record ChatMessage(@NotBlank String message) {
    }

    public record ChatReply(String reply, int iterations) {
    }
}
