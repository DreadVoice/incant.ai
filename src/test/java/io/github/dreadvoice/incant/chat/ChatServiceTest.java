package io.github.dreadvoice.incant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.dreadvoice.incant.agent.SystemPromptBuilder;
import io.github.dreadvoice.incant.agent.ToolDispatcher;
import io.github.dreadvoice.incant.conversation.Conversation;
import io.github.dreadvoice.incant.conversation.ConversationRepository;
import io.github.dreadvoice.incant.conversation.Message;
import io.github.dreadvoice.incant.conversation.MessageRepository;
import io.github.dreadvoice.incant.conversation.MessageRole;
import io.github.dreadvoice.incant.provider.ChatModelResolver;
import io.github.dreadvoice.incant.provider.ProviderProperties;
import io.github.dreadvoice.incant.skill.LocalDirSkillSource;
import io.github.dreadvoice.incant.skill.SkillLoader;
import io.github.dreadvoice.incant.skill.SkillRegistry;

@DataJpaTest
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

    private StubModel model;

    private ChatService service;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + databaseDirectory.resolve("incant-test.db"));
    }

    @BeforeEach
    void setUp() {
        model = new StubModel();
        service = new ChatService(resolver(model), new ToolDispatcher(List.of()), promptBuilder(),
                conversations, messages, 10);
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

    private static ChatModelResolver resolver(ChatModel model) {
        return new ChatModelResolver(new ProviderProperties()) {

            @Override
            public Resolved resolve(String requestedProvider, String requestedModel) {
                return new Resolved("ollama", "incant-qwen", model);
            }
        };
    }

    private static final class StubModel implements ChatModel {

        private final Deque<AiMessage> replies = new ArrayDeque<>();
        private final List<ChatRequest> requests = new ArrayList<>();

        private void willReply(String text) {
            replies.add(AiMessage.from(text));
        }

        private ChatRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            AiMessage reply = replies.isEmpty() ? AiMessage.from("out of stubbed replies") : replies.poll();
            return ChatResponse.builder().aiMessage(reply).build();
        }
    }
}
