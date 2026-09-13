package io.github.dreadvoice.incant.chat;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.dreadvoice.incant.agent.AgentOrchestrator;
import io.github.dreadvoice.incant.agent.SkillTools;
import io.github.dreadvoice.incant.agent.StreamingAgentOrchestrator;
import io.github.dreadvoice.incant.agent.SystemPromptBuilder;
import io.github.dreadvoice.incant.agent.ToolDispatcher;
import io.github.dreadvoice.incant.conversation.Conversation;
import io.github.dreadvoice.incant.conversation.ConversationStore;
import io.github.dreadvoice.incant.conversation.Message;
import io.github.dreadvoice.incant.provider.ChatModelResolver;

@Service
public class ChatService {

    private static final String BASE_PROMPT = "You are Incant, a local runtime that runs published skills on the "
            + "model provider the user configured. Answer the user directly and use a skill only when it applies.";

    private static final int TITLE_LENGTH = 80;

    private final ChatModelResolver models;
    private final ToolDispatcher dispatcher;
    private final SystemPromptBuilder promptBuilder;
    private final ConversationStore store;
    private final int maxIterations;

    public ChatService(ChatModelResolver models, ToolDispatcher dispatcher, SystemPromptBuilder promptBuilder,
            ConversationStore store, @Value("${incant.max-iterations}") int maxIterations) {
        this.models = models;
        this.dispatcher = dispatcher;
        this.promptBuilder = promptBuilder;
        this.store = store;
        this.maxIterations = maxIterations;
    }

    public Turn send(Long conversationId, String message, String requestedProvider, String requestedModel) {
        ChatModelResolver.Resolved resolved = models.resolve(requestedProvider, requestedModel);
        List<ChatMessage> history = history(conversationId);

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                resolved.chatModel(), dispatcher, SkillTools.all(), maxIterations);
        AgentOrchestrator.Result result = orchestrator.run(systemPrompt(), history, message);

        return record(conversationId, message, result, resolved.provider(), resolved.model());
    }

    public void stream(Long conversationId, String message, String requestedProvider, String requestedModel,
            StreamListener listener) {
        ChatModelResolver.ResolvedStream resolved = models.resolveStreaming(requestedProvider, requestedModel);
        List<ChatMessage> history = history(conversationId);

        StreamingAgentOrchestrator orchestrator = new StreamingAgentOrchestrator(
                resolved.chatModel(), dispatcher, SkillTools.all(), maxIterations);

        orchestrator.run(systemPrompt(), history, message, new StreamingAgentOrchestrator.Listener() {

            @Override
            public void onToken(String token) {
                listener.onToken(token);
            }

            @Override
            public void onToolCall(AgentOrchestrator.ToolCall call) {
                SkillTools.loadedSkills(List.of(call)).forEach(listener::onSkill);
            }

            @Override
            public void onComplete(AgentOrchestrator.Result result) {
                try {
                    listener.onComplete(
                            record(conversationId, message, result, resolved.provider(), resolved.model()));
                } catch (RuntimeException e) {
                    listener.onError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                listener.onError(error);
            }
        });
    }

    private Turn record(Long conversationId, String message, AgentOrchestrator.Result result, String provider,
            String model) {
        Conversation conversation = store.recordTurn(
                conversationId, title(message), message, result.text(), provider, model);

        return new Turn(conversation.getId(), result.text(), provider, model,
                SkillTools.loadedSkills(result.toolCalls()), result.telemetry());
    }

    private String systemPrompt() {
        return promptBuilder.build(BASE_PROMPT, false, false);
    }

    private List<ChatMessage> history(Long conversationId) {
        if (conversationId == null) {
            return List.of();
        }

        store.require(conversationId);
        return store.history(conversationId).stream()
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

    public interface StreamListener {

        void onToken(String token);

        void onSkill(String skill);

        void onComplete(Turn turn);

        void onError(Throwable error);
    }

    public record Turn(Long conversationId, String reply, String provider, String model, List<String> skills,
            AgentOrchestrator.Telemetry telemetry) {
    }
}
