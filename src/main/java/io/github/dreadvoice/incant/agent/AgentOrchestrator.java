package io.github.dreadvoice.incant.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public final class AgentOrchestrator {

    public static final int DEFAULT_MAX_ITERATIONS = 10;

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

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

        long startedAt = System.currentTimeMillis();
        TokenUsage usage = new TokenUsage();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            ChatResponse response = model.chat(request(messages));
            AiMessage reply = response.aiMessage();
            usage = usage.add(response.tokenUsage());
            messages.add(reply);

            logTurn(iteration, response.tokenUsage(), reply.toolExecutionRequests().size());

            if (!reply.hasToolExecutionRequests()) {
                Telemetry telemetry = telemetry(iteration, usage, startedAt);
                log.info("turn finished after {} iterations, {} tokens in {} ms",
                        telemetry.iterations(), telemetry.totalTokens(), telemetry.durationMillis());
                return new Result(reply.text(), List.copyOf(messages), iteration, telemetry);
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

    private static void logTurn(int iteration, TokenUsage usage, int toolCalls) {
        log.debug("iteration {}: {} input tokens, {} output tokens, {} tool calls",
                iteration, count(usage == null ? null : usage.inputTokenCount()),
                count(usage == null ? null : usage.outputTokenCount()), toolCalls);
    }

    private static Telemetry telemetry(int iterations, TokenUsage usage, long startedAt) {
        return new Telemetry(iterations,
                count(usage.inputTokenCount()),
                count(usage.outputTokenCount()),
                System.currentTimeMillis() - startedAt);
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    public record Result(String text, List<ChatMessage> messages, int iterations, Telemetry telemetry) {
    }

    public record Telemetry(int iterations, int inputTokens, int outputTokens, long durationMillis) {

        public int totalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
