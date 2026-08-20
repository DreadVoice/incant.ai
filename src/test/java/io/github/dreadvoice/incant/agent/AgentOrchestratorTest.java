package io.github.dreadvoice.incant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

class AgentOrchestratorTest {

    private static final String LOAD_SKILL = SystemPromptBuilder.LOAD_SKILL_TOOL;

    @Test
    void returnsTextWhenModelAsksForNoTools() {
        StubModel model = new StubModel(AiMessage.from("straight answer"));

        AgentOrchestrator.Result result = orchestrator(model).run("system prompt", "hello");

        assertThat(result.text()).isEqualTo("straight answer");
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.messages()).hasSize(3);
    }

    @Test
    void runsToolCallThenReturnsFinalText() {
        StubModel model = new StubModel(
                AiMessage.from(call("1", "writing-clearly")),
                AiMessage.from("final answer"));

        AgentOrchestrator.Result result = orchestrator(model).run("system prompt", "hello");

        assertThat(result.text()).isEqualTo("final answer");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.messages()).hasExactlyElementsOfTypes(
                SystemMessage.class,
                UserMessage.class,
                AiMessage.class,
                ToolExecutionResultMessage.class,
                AiMessage.class);
    }

    @Test
    void feedsToolResultBackToModel() {
        StubModel model = new StubModel(
                AiMessage.from(call("1", "writing-clearly")),
                AiMessage.from("final answer"));

        AgentOrchestrator.Result result = orchestrator(model).run("system prompt", "hello");

        ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) result.messages().get(3);
        assertThat(toolResult.id()).isEqualTo("1");
        assertThat(toolResult.toolName()).isEqualTo(LOAD_SKILL);
        assertThat(toolResult.text()).isEqualTo("instructions for writing-clearly");
    }

    @Test
    void executesEveryToolCallInOneReply() {
        StubModel model = new StubModel(
                AiMessage.from(List.of(call("1", "writing-clearly"), call("2", "csv-report"))),
                AiMessage.from("final answer"));

        AgentOrchestrator.Result result = orchestrator(model).run("system prompt", "hello");

        assertThat(result.messages()).hasSize(6);
        assertThat(result.messages().subList(3, 5))
                .extracting(message -> ((ToolExecutionResultMessage) message).text())
                .containsExactly("instructions for writing-clearly", "instructions for csv-report");
    }

    @Test
    void reportsToolFailureAsToolResult() {
        StubModel model = new StubModel(
                AiMessage.from(call("1", "missing")),
                AiMessage.from("final answer"));

        AgentOrchestrator.Result result = orchestrator(model).run("system prompt", "hello");

        assertThat(((ToolExecutionResultMessage) result.messages().get(3)).text())
                .startsWith("Error: tool '" + LOAD_SKILL + "' failed:");
        assertThat(result.text()).isEqualTo("final answer");
    }

    @Test
    void sendsHistoryAndToolSpecificationsOnEveryRequest() {
        StubModel model = new StubModel(
                AiMessage.from(call("1", "writing-clearly")),
                AiMessage.from("final answer"));

        orchestrator(model).run("system prompt", "hello");

        assertThat(model.requests).hasSize(2);
        assertThat(model.requests.get(0).messages()).hasSize(2);
        assertThat(model.requests.get(1).messages()).hasSize(4);
        assertThat(model.requests.get(1).toolSpecifications())
                .extracting(spec -> spec.name())
                .containsExactly(LOAD_SKILL);
    }

    @Test
    void omitsSystemMessageWhenPromptIsBlank() {
        StubModel model = new StubModel(AiMessage.from("straight answer"));

        AgentOrchestrator.Result result = orchestrator(model).run("  ", "hello");

        assertThat(result.messages()).hasExactlyElementsOfTypes(UserMessage.class, AiMessage.class);
    }

    @Test
    void stopsAtMaxIterations() {
        StubModel model = new StubModel(
                AiMessage.from(call("1", "writing-clearly")),
                AiMessage.from(call("2", "writing-clearly")),
                AiMessage.from(call("3", "writing-clearly")));

        AgentOrchestrator agent = new AgentOrchestrator(model, dispatcher(), SkillTools.all(), 2);

        assertThatThrownBy(() -> agent.run("system prompt", "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("within 2 iterations");
        assertThat(model.requests).hasSize(2);
    }

    @Test
    void rejectsMaxIterationsBelowOne() {
        assertThatThrownBy(() -> new AgentOrchestrator(new StubModel(), dispatcher(), SkillTools.all(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIterations");
    }

    @Test
    void rejectsBlankUserMessage() {
        assertThatThrownBy(() -> orchestrator(new StubModel()).run("system prompt", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userMessage");
    }

    private static AgentOrchestrator orchestrator(ChatModel model) {
        return new AgentOrchestrator(model, dispatcher(), SkillTools.all());
    }

    private static ToolDispatcher dispatcher() {
        return new ToolDispatcher(List.of(new StubHandler()));
    }

    private static ToolExecutionRequest call(String id, String skillName) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(LOAD_SKILL)
                .arguments("{\"" + SkillTools.NAME_ARGUMENT + "\":\"" + skillName + "\"}")
                .build();
    }

    private static final class StubHandler implements ToolHandler {

        @Override
        public String name() {
            return LOAD_SKILL;
        }

        @Override
        public String execute(Map<String, Object> arguments) {
            String skillName = String.valueOf(arguments.get(SkillTools.NAME_ARGUMENT));
            if ("missing".equals(skillName)) {
                throw new IllegalArgumentException("unknown skill '" + skillName + "'");
            }
            return "instructions for " + skillName;
        }
    }

    private static final class StubModel implements ChatModel {

        private final Deque<AiMessage> replies;
        private final List<ChatRequest> requests = new ArrayList<>();

        private StubModel(AiMessage... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            AiMessage reply = replies.isEmpty() ? AiMessage.from("out of stubbed replies") : replies.poll();
            return ChatResponse.builder().aiMessage(reply).build();
        }
    }
}
