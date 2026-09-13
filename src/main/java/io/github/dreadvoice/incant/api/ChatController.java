package io.github.dreadvoice.incant.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.dreadvoice.incant.agent.AgentOrchestrator;
import io.github.dreadvoice.incant.chat.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@Valid @RequestBody ChatMessage request) {
        ChatService.Turn turn = chat.send(
                request.conversationId(), request.message(), request.provider(), request.model());

        return new ChatReply(turn.reply(), turn.provider(), turn.model(), turn.conversationId(), turn.skills(),
                turn.telemetry());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    public record ChatMessage(@NotBlank String message, Long conversationId, String provider, String model) {
    }

    public record ChatReply(String reply, String provider, String model, Long conversationId, List<String> skills,
            AgentOrchestrator.Telemetry telemetry) {
    }
}
