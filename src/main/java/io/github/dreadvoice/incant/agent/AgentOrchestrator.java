package io.github.dreadvoice.incant.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

public final class AgentOrchestrator {

    public static final int DEFAULT_MAX_ITERATIONS = 10;

    private final ChatModel model;
    private final ToolDispatcher dispatcher;
    private final List<ToolSpecification> tools;
    private final int maxIterations;

    public AgentOrchestrator(ChatModel model, ToolDispatcher dispatcher, List<ToolSpecification> tools) {
        this(model, dispatcher, tools, DEFAULT_MAX_ITERATIONS);
    }

    public AgentOrchestrator(ChatModel model, ToolDispatcher dispatcher, List<ToolSpecification> tools,
            int maxIterations) {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1");
        }
        this.model = Objects.requireNonNull(model, "model");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        this.maxIterations = maxIterations;
    }

    public int maxIterations() {
        return maxIterations;
    }

    public Result run(String systemPrompt, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }

        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userMessage));

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            AiMessage reply = model.chat(request(messages)).aiMessage();
            messages.add(reply);

            if (!reply.hasToolExecutionRequests()) {
                return new Result(reply.text(), List.copyOf(messages), iteration);
            }

            for (ToolExecutionRequest call : reply.toolExecutionRequests()) {
                messages.add(ToolExecutionResultMessage.from(call, dispatcher.dispatch(call)));
            }
        }

        throw new IllegalStateException(
                "agent did not produce a final answer within " + maxIterations + " iterations");
    }

    private ChatRequest request(List<ChatMessage> messages) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(List.copyOf(messages));
        if (!tools.isEmpty()) {
            builder.toolSpecifications(tools);
        }
        return builder.build();
    }

    public record Result(String text, List<ChatMessage> messages, int iterations) {
    }
}
