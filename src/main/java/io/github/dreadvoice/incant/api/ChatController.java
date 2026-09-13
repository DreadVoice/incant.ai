package io.github.dreadvoice.incant.api;

import java.io.IOException;
import java.time.Duration;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.dreadvoice.incant.agent.AgentOrchestrator;
import io.github.dreadvoice.incant.chat.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(10);
    private static final String TOKEN_EVENT = "token";
    private static final String SKILL_EVENT = "skill";
    private static final String DONE_EVENT = "done";
    private static final String ERROR_EVENT = "error";

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

    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatMessage request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());

        try {
            chat.stream(request.conversationId(), request.message(), request.provider(), request.model(),
                    new EmitterListener(emitter));
        } catch (RuntimeException e) {
            sendError(emitter, e);
        }

        return emitter;
    }

    private static void sendError(SseEmitter emitter, Throwable error) {
        try {
            emitter.send(SseEmitter.event().name(ERROR_EVENT).data(Map.of("error", messageOf(error))));
            emitter.complete();
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        }
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private record EmitterListener(SseEmitter emitter) implements ChatService.StreamListener {

        @Override
        public void onToken(String token) {
            send(TOKEN_EVENT, Map.of("text", token));
        }

        @Override
        public void onSkill(String skill) {
            send(SKILL_EVENT, Map.of("name", skill));
        }

        @Override
        public void onComplete(ChatService.Turn turn) {
            send(DONE_EVENT, new ChatReply(turn.reply(), turn.provider(), turn.model(), turn.conversationId(),
                    turn.skills(), turn.telemetry()));
            emitter.complete();
        }

        @Override
        public void onError(Throwable error) {
            log.warn("streamed turn failed: {}", messageOf(error));
            sendError(emitter, error);
        }

        private void send(String event, Object payload) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitter.complete();
            }
        }
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
