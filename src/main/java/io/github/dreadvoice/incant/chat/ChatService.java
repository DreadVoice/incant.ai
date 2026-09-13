package io.github.dreadvoice.incant.chat;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.dreadvoice.incant.agent.AgentOrchestrator;
import io.github.dreadvoice.incant.agent.SkillTools;
import io.github.dreadvoice.incant.agent.SystemPromptBuilder;
import io.github.dreadvoice.incant.agent.ToolDispatcher;
import io.github.dreadvoice.incant.conversation.Conversation;
import io.github.dreadvoice.incant.conversation.ConversationRepository;
import io.github.dreadvoice.incant.conversation.Message;
import io.github.dreadvoice.incant.conversation.MessageRepository;
import io.github.dreadvoice.incant.conversation.MessageRole;
import io.github.dreadvoice.incant.provider.ChatModelResolver;

@Service
public class ChatService {

    private static final String BASE_PROMPT = "You are Incant, a local runtime that runs published skills on the "
            + "model provider the user configured. Answer the user directly and use a skill only when it applies.";

    private static final int TITLE_LENGTH = 80;

    private final ChatModelResolver models;
    private final ToolDispatcher dispatcher;
    private final SystemPromptBuilder promptBuilder;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final int maxIterations;

    public ChatService(ChatModelResolver models, ToolDispatcher dispatcher, SystemPromptBuilder promptBuilder,
            ConversationRepository conversations, MessageRepository messages,
            @Value("${incant.max-iterations}") int maxIterations) {
        this.models = models;
        this.dispatcher = dispatcher;
        this.promptBuilder = promptBuilder;
        this.conversations = conversations;
        this.messages = messages;
        this.maxIterations = maxIterations;
    }

    @Transactional
    public Turn send(Long conversationId, String message, String requestedProvider, String requestedModel) {
        ChatModelResolver.Resolved resolved = models.resolve(requestedProvider, requestedModel);
        Conversation conversation = conversationId == null
                ? conversations.save(new Conversation(title(message), resolved.provider(), resolved.model()))
                : load(conversationId);

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                resolved.chatModel(), dispatcher, SkillTools.all(), maxIterations);
        AgentOrchestrator.Result result = orchestrator.run(
                promptBuilder.build(BASE_PROMPT, false, false), history(conversation.getId()), message);

        messages.save(new Message(conversation, MessageRole.USER, message));
        String reply = result.text();
        if (reply != null && !reply.isBlank()) {
            messages.save(new Message(conversation, MessageRole.ASSISTANT, reply));
        }

        conversation.setProvider(resolved.provider());
        conversation.setModelName(resolved.model());
        conversations.save(conversation);

        return new Turn(conversation.getId(), reply, resolved.provider(), resolved.model(),
                SkillTools.loadedSkills(result.toolCalls()), result.telemetry());
    }

    private Conversation load(Long conversationId) {
        return conversations.findById(conversationId).orElseThrow(
                () -> new IllegalArgumentException("unknown conversation '" + conversationId + "'"));
    }

    private List<ChatMessage> history(Long conversationId) {
        return messages.findByConversationIdOrderByIdAsc(conversationId).stream()
                .map(ChatService::toModelMessage)
                .toList();
    }

    private static ChatMessage toModelMessage(Message message) {
        return switch (message.getRole()) {
            case USER -> UserMessage.from(message.getContent());
            case ASSISTANT -> AiMessage.from(message.getContent());
            case SYSTEM, TOOL -> throw new IllegalStateException(
                    "stored conversation holds an unsupported role: " + message.getRole());
        };
    }

    private static String title(String message) {
        String firstLine = message.strip().lines().findFirst().orElse("").strip();
        if (firstLine.length() <= TITLE_LENGTH) {
            return firstLine;
        }
        return firstLine.substring(0, TITLE_LENGTH - 1).strip() + "…";
    }

    public record Turn(Long conversationId, String reply, String provider, String model, List<String> skills,
            AgentOrchestrator.Telemetry telemetry) {
    }
}
