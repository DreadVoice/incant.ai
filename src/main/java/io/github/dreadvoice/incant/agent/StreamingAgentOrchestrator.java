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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;

public final class StreamingAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(StreamingAgentOrchestrator.class);

    private final StreamingChatModel model;
    private final ToolDispatcher dispatcher;
    private final List<ToolSpecification> tools;
    private final int maxIterations;

    public StreamingAgentOrchestrator(StreamingChatModel model, ToolDispatcher dispatcher,
            List<ToolSpecification> tools, int maxIterations) {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1");
        }
        this.model = Objects.requireNonNull(model, "model");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        this.maxIterations = maxIterations;
    }

    public void run(String systemPrompt, List<ChatMessage> history, String userMessage, Listener listener) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(listener, "listener");

        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.addAll(history);
        messages.add(UserMessage.from(userMessage));

        step(messages, new Turn(), 1, listener);
    }

    private void step(List<ChatMessage> messages, Turn turn, int iteration, Listener listener) {
        model.chat(request(messages), new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String token) {
                if (token != null && !token.isEmpty()) {
                    listener.onToken(token);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                try {
                    advance(response);
                } catch (RuntimeException e) {
                    listener.onError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                listener.onError(error);
            }

            private void advance(ChatResponse response) {
                AiMessage reply = response.aiMessage();
                turn.usage = turn.usage.add(response.tokenUsage());
                messages.add(reply);

                if (!reply.hasToolExecutionRequests()) {
                    AgentOrchestrator.Telemetry telemetry = turn.telemetry(iteration);
                    log.info("streamed turn finished after {} iterations, {} tokens in {} ms",
                            telemetry.iterations(), telemetry.totalTokens(), telemetry.durationMillis());
                    listener.onComplete(new AgentOrchestrator.Result(reply.text(), List.copyOf(messages), iteration,
                            telemetry, List.copyOf(turn.toolCalls)));
                    return;
                }

                if (iteration >= maxIterations) {
                    listener.onError(new IllegalStateException(
                            "agent did not produce a final answer within " + maxIterations + " iterations"));
                    return;
                }

                for (ToolExecutionRequest call : reply.toolExecutionRequests()) {
                    ToolDispatcher.Dispatch dispatch = dispatcher.dispatch(call);
                    AgentOrchestrator.ToolCall recorded =
                            new AgentOrchestrator.ToolCall(call.name(), dispatch.arguments(), dispatch.failed());
                    turn.toolCalls.add(recorded);
                    listener.onToolCall(recorded);
                    messages.add(ToolExecutionResultMessage.from(call, dispatch.text()));
                }

                step(messages, turn, iteration + 1, listener);
            }
        });
    }

    private ChatRequest request(List<ChatMessage> messages) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(List.copyOf(messages));
        if (!tools.isEmpty()) {
            builder.toolSpecifications(tools);
        }
        return builder.build();
    }

    public interface Listener {

        void onToken(String token);

        void onToolCall(AgentOrchestrator.ToolCall call);

        void onComplete(AgentOrchestrator.Result result);

        void onError(Throwable error);
    }

    private static final class Turn {

        private final List<AgentOrchestrator.ToolCall> toolCalls = new ArrayList<>();
        private final long startedAt = System.currentTimeMillis();
        private TokenUsage usage = new TokenUsage();

        private AgentOrchestrator.Telemetry telemetry(int iterations) {
            return new AgentOrchestrator.Telemetry(iterations, count(usage.inputTokenCount()),
                    count(usage.outputTokenCount()), System.currentTimeMillis() - startedAt);
        }

        private static int count(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
