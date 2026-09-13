package io.github.dreadvoice.incant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.dreadvoice.incant.agent.SkillTools;
import io.github.dreadvoice.incant.agent.SystemPromptBuilder;
import io.github.dreadvoice.incant.agent.ToolDispatcher;
import io.github.dreadvoice.incant.agent.ToolHandler;
import io.github.dreadvoice.incant.conversation.Conversation;
import io.github.dreadvoice.incant.conversation.ConversationRepository;
import io.github.dreadvoice.incant.conversation.ConversationStore;
import io.github.dreadvoice.incant.conversation.Message;
import io.github.dreadvoice.incant.conversation.MessageRepository;
import io.github.dreadvoice.incant.conversation.MessageRole;
import io.github.dreadvoice.incant.provider.ChatModelResolver;
import io.github.dreadvoice.incant.provider.ProviderProperties;
import io.github.dreadvoice.incant.skill.LocalDirSkillSource;
import io.github.dreadvoice.incant.skill.SkillLoader;
import io.github.dreadvoice.incant.skill.SkillRegistry;

@DataJpaTest
@Import(ConversationStore.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatServiceTest {

    @TempDir
    static Path databaseDirectory;

    @TempDir
    static Path skillsDirectory;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private ConversationStore store;

    private Script model;

    private ChatService service;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + databaseDirectory.resolve("incant-test.db"));
    }

    @BeforeEach
    void setUp() {
        model = new Script();
        service = new ChatService(resolver(model), new ToolDispatcher(List.of(new StubSkillLoader())),
                promptBuilder(), store, 10);
    }

    @Test
    void firstTurnCreatesAConversationAndStoresBothSides() {
        model.willReply("the first answer");

        ChatService.Turn turn = service.send(null, "the first question", null, null);

        assertThat(turn.conversationId()).isNotNull();
        assertThat(turn.reply()).isEqualTo("the first answer");
        assertThat(conversations.findById(turn.conversationId())).isPresent();
        assertThat(messages.findByConversationIdOrderByIdAsc(turn.conversationId()))
                .extracting(Message::getRole, Message::getContent)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(MessageRole.USER, "the first question"),
                        org.assertj.core.api.Assertions.tuple(MessageRole.ASSISTANT, "the first answer"));
    }

    @Test
    void secondTurnReplaysTheStoredHistoryToTheModel() {
        model.willReply("the first answer");
        ChatService.Turn first = service.send(null, "the first question", null, null);

        model.willReply("the second answer");
        service.send(first.conversationId(), "the second question", null, null);

        assertThat(model.lastRequest().messages()).hasExactlyElementsOfTypes(
                SystemMessage.class,
                UserMessage.class,
                AiMessage.class,
                UserMessage.class);
        assertThat(text(model.lastRequest().messages()))
                .containsExactly("the first question", "the first answer", "the second question");
    }

    @Test
    void everyTurnOfOneConversationIsStoredInOrder() {
        model.willReply("first answer");
        ChatService.Turn first = service.send(null, "first question", null, null);

        model.willReply("second answer");
        service.send(first.conversationId(), "second question", null, null);

        assertThat(messages.findByConversationIdOrderByIdAsc(first.conversationId()))
                .extracting(Message::getContent)
                .containsExactly("first question", "first answer", "second question", "second answer");
        assertThat(conversations.count()).isEqualTo(1);
    }

    @Test
    void separateConversationsDoNotShareHistory() {
        model.willReply("first answer");
        service.send(null, "first question", null, null);

        model.willReply("unrelated answer");
        ChatService.Turn other = service.send(null, "unrelated question", null, null);

        assertThat(text(model.lastRequest().messages())).containsExactly("unrelated question");
        assertThat(messages.findByConversationIdOrderByIdAsc(other.conversationId()))
                .extracting(Message::getContent)
                .containsExactly("unrelated question", "unrelated answer");
    }

    @Test
    void titleComesFromTheFirstMessage() {
        model.willReply("answer");

        ChatService.Turn turn = service.send(null, "Summarise the writing-clearly skill", null, null);

        Conversation conversation = conversations.findById(turn.conversationId()).orElseThrow();
        assertThat(conversation.getTitle()).isEqualTo("Summarise the writing-clearly skill");
        assertThat(conversation.getProvider()).isEqualTo("ollama");
        assertThat(conversation.getModelName()).isEqualTo("incant-qwen");
    }

    @Test
    void reportsTheSkillsLoadedDuringTheTurn() {
        model.willRequestSkill("writing-clearly");
        model.willReply("an answer that used the skill");

        ChatService.Turn turn = service.send(null, "use writing-clearly", null, null);

        assertThat(turn.skills()).containsExactly("writing-clearly");
        assertThat(turn.telemetry().iterations()).isEqualTo(2);
    }

    @Test
    void reportsNoSkillsWhenTheModelAnswersOnItsOwn() {
        model.willReply("a straight answer");

        ChatService.Turn turn = service.send(null, "just answer", null, null);

        assertThat(turn.skills()).isEmpty();
    }

    @Test
    void doesNotReportASkillThatFailedToLoad() {
        model.willRequestSkill("missing");
        model.willReply("the skill could not be read");

        ChatService.Turn turn = service.send(null, "use missing", null, null);

        assertThat(turn.skills()).isEmpty();
    }

    @Test
    void streamingEmitsTokensAndStoresTheTurn() {
        model.willReply("a streamed answer");
        RecordingListener listener = new RecordingListener();

        service.stream(null, "stream this", null, null, listener);

        assertThat(listener.error).isNull();
        assertThat(listener.tokens).containsExactly("a ", "streamed ", "answer ");
        assertThat(listener.streamedText()).isEqualTo("a streamed answer");
        assertThat(listener.turn.reply()).isEqualTo("a streamed answer");
        assertThat(messages.findByConversationIdOrderByIdAsc(listener.turn.conversationId()))
                .extracting(Message::getRole, Message::getContent)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(MessageRole.USER, "stream this"),
                        org.assertj.core.api.Assertions.tuple(MessageRole.ASSISTANT, "a streamed answer"));
    }

    @Test
    void streamingReportsSkillLoadsAsTheyHappen() {
        model.willRequestSkill("writing-clearly");
        model.willReply("the streamed answer");
        RecordingListener listener = new RecordingListener();

        service.stream(null, "use writing-clearly", null, null, listener);

        assertThat(listener.skills).containsExactly("writing-clearly");
        assertThat(listener.turn.skills()).containsExactly("writing-clearly");
    }

    @Test
    void streamingContinuesAnExistingConversation() {
        model.willReply("first answer");
        ChatService.Turn first = service.send(null, "first question", null, null);

        model.willReply("second answer");
        RecordingListener listener = new RecordingListener();
        service.stream(first.conversationId(), "second question", null, null, listener);

        assertThat(listener.turn.conversationId()).isEqualTo(first.conversationId());
        assertThat(text(model.lastRequest().messages()))
                .containsExactly("first question", "first answer", "second question");
        assertThat(messages.findByConversationIdOrderByIdAsc(first.conversationId()))
                .extracting(Message::getContent)
                .containsExactly("first question", "first answer", "second question", "second answer");
    }

    @Test
    void aPersistedAnswerKeepsTheSkillsItLoaded() {
        model.willRequestSkill("writing-clearly");
        model.willReply("an answer that used the skill");

        ChatService.Turn turn = service.send(null, "use writing-clearly", null, null);

        assertThat(store.history(turn.conversationId()))
                .filteredOn(message -> message.getRole() == MessageRole.ASSISTANT)
                .singleElement()
                .extracting(Message::getSkills)
                .isEqualTo(List.of("writing-clearly"));
    }

    @Test
    void unknownConversationIsRejected() {
        model.willReply("answer");

        assertThatThrownBy(() -> service.send(4242L, "a question", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown conversation");
    }

    private static List<String> text(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .map(message -> message instanceof UserMessage user
                        ? user.singleText()
                        : ((AiMessage) message).text())
                .toList();
    }

    private static SystemPromptBuilder promptBuilder() {
        SkillRegistry registry = new SkillRegistry(new SkillLoader(new LocalDirSkillSource(skillsDirectory)));
        registry.refresh();
        return new SystemPromptBuilder(registry);
    }

    private static ChatModelResolver resolver(Script script) {
        return new ChatModelResolver(new ProviderProperties()) {

            @Override
            public Resolved resolve(String requestedProvider, String requestedModel) {
                return new Resolved("ollama", "incant-qwen", new StubModel(script));
            }

            @Override
            public ResolvedStream resolveStreaming(String requestedProvider, String requestedModel) {
                return new ResolvedStream("ollama", "incant-qwen", new StubStreamingModel(script));
            }
        };
    }

    private static final class StubSkillLoader implements ToolHandler {

        @Override
        public String name() {
            return SystemPromptBuilder.LOAD_SKILL_TOOL;
        }

        @Override
        public String execute(Map<String, Object> arguments) {
            String skill = String.valueOf(arguments.get(SkillTools.NAME_ARGUMENT));
            if ("missing".equals(skill)) {
                throw new IllegalArgumentException("unknown skill '" + skill + "'");
            }
            return "instructions for " + skill;
        }
    }

    private static final class Script {

        private final Deque<AiMessage> replies = new ArrayDeque<>();
        private final List<ChatRequest> requests = new ArrayList<>();

        private void willReply(String text) {
            replies.add(AiMessage.from(text));
        }

        private void willRequestSkill(String skill) {
            replies.add(AiMessage.from(ToolExecutionRequest.builder()
                    .id("1")
                    .name(SystemPromptBuilder.LOAD_SKILL_TOOL)
                    .arguments("{\"" + SkillTools.NAME_ARGUMENT + "\":\"" + skill + "\"}")
                    .build()));
        }

        private ChatRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }

        private ChatResponse answer(ChatRequest request) {
            requests.add(request);
            AiMessage reply = replies.isEmpty() ? AiMessage.from("out of stubbed replies") : replies.poll();
            return ChatResponse.builder().aiMessage(reply).build();
        }
    }

    private record StubModel(Script script) implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return script.answer(request);
        }
    }

    private record StubStreamingModel(Script script) implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            ChatResponse response = script.answer(request);
            String text = response.aiMessage().text();
            if (text != null) {
                for (String word : text.split(" ")) {
                    handler.onPartialResponse(word + " ");
                }
            }
            handler.onCompleteResponse(response);
        }
    }

    private static final class RecordingListener implements ChatService.StreamListener {

        private final List<String> tokens = new ArrayList<>();
        private final List<String> skills = new ArrayList<>();
        private ChatService.Turn turn;
        private Throwable error;

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onSkill(String skill) {
            skills.add(skill);
        }

        @Override
        public void onComplete(ChatService.Turn completed) {
            turn = completed;
        }

        @Override
        public void onError(Throwable cause) {
            error = cause;
        }

        private String streamedText() {
            return String.join("", tokens).strip();
        }
    }
}
