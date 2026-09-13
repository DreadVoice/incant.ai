package io.github.dreadvoice.incant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.dreadvoice.incant.conversation.Conversation;
import io.github.dreadvoice.incant.conversation.ConversationStore;
import io.github.dreadvoice.incant.conversation.Message;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationStore store;

    public ConversationController(ConversationStore store) {
        this.store = store;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ConversationSummary> list() {
        return store.recent().stream()
                .map(conversation -> new ConversationSummary(
                        conversation.getId(),
                        conversation.getTitle(),
                        conversation.getProvider(),
                        conversation.getModelName(),
                        conversation.getUpdatedAt(),
                        store.messageCount(conversation.getId())))
                .toList();
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ConversationDetail detail(@PathVariable Long id) {
        Conversation conversation = store.require(id);

        List<StoredMessage> messages = store.history(id).stream()
                .map(message -> new StoredMessage(
                        message.getId(),
                        message.getRole().name().toLowerCase(java.util.Locale.ROOT),
                        message.getContent(),
                        message.getCreatedAt()))
                .toList();

        return new ConversationDetail(conversation.getId(), conversation.getTitle(), conversation.getProvider(),
                conversation.getModelName(), messages);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleUnknownConversation(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    public record ConversationSummary(Long id, String title, String provider, String model, Instant updatedAt,
            long messageCount) {
    }

    public record ConversationDetail(Long id, String title, String provider, String model,
            List<StoredMessage> messages) {
    }

    public record StoredMessage(Long id, String role, String content, Instant createdAt) {
    }
}
