package io.github.dreadvoice.incant.conversation;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConversationStore {

    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ConversationStore(ConversationRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public Conversation require(Long conversationId) {
        return load(conversationId);
    }

    @Transactional(readOnly = true)
    public List<Conversation> recent() {
        return conversations.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public long messageCount(Long conversationId) {
        return messages.countByConversationId(conversationId);
    }

    @Transactional(readOnly = true)
    public List<Message> history(Long conversationId) {
        return messages.findByConversationIdOrderByIdAsc(conversationId);
    }

    @Transactional
    public Conversation recordTurn(Long conversationId, String title, String userMessage, String reply,
            String provider, String model) {
        Conversation conversation = conversationId == null
                ? conversations.save(new Conversation(title, provider, model))
                : load(conversationId);

        messages.save(new Message(conversation, MessageRole.USER, userMessage));
        if (reply != null && !reply.isBlank()) {
            messages.save(new Message(conversation, MessageRole.ASSISTANT, reply));
        }

        conversation.setProvider(provider);
        conversation.setModelName(model);
        conversation.touch();
        return conversations.save(conversation);
    }

    private Conversation load(Long conversationId) {
        return conversations.findById(conversationId).orElseThrow(
                () -> new IllegalArgumentException("unknown conversation '" + conversationId + "'"));
    }
}
