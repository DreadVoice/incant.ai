package io.github.dreadvoice.incant.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@Import(ConversationStore.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConversationStoreTest {

    @TempDir
    static Path databaseDirectory;

    @Autowired
    private ConversationStore store;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + databaseDirectory.resolve("conversation-store-test.db"));
    }

    @Test
    void listsTheMostRecentlyUpdatedConversationFirst() {
        Long older = store.recordTurn(null, "older", "first question", "first answer", "ollama", "incant-qwen")
                .getId();
        Long newer = store.recordTurn(null, "newer", "second question", "second answer", "ollama", "incant-qwen")
                .getId();

        sleepPastTheClockTick();
        store.recordTurn(older, "older", "a later question", "a later answer", "ollama", "incant-qwen");

        assertThat(store.recent()).extracting(Conversation::getId).containsExactly(older, newer);
    }

    @Test
    void countsTheStoredMessages() {
        Long conversation = store.recordTurn(null, "counted", "a question", "an answer", "ollama", "incant-qwen")
                .getId();

        assertThat(store.messageCount(conversation)).isEqualTo(2);
    }

    @Test
    void returnsTheStoredMessagesInOrder() {
        Long conversation = store.recordTurn(null, "ordered", "first question", "first answer", "ollama", "m")
                .getId();
        store.recordTurn(conversation, "ordered", "second question", "second answer", "ollama", "m");

        assertThat(store.history(conversation))
                .extracting(Message::getRole, Message::getContent)
                .containsExactly(
                        tuple(MessageRole.USER, "first question"),
                        tuple(MessageRole.ASSISTANT, "first answer"),
                        tuple(MessageRole.USER, "second question"),
                        tuple(MessageRole.ASSISTANT, "second answer"));
    }

    private static void sleepPastTheClockTick() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void rejectsAnUnknownConversation() {
        assertThatThrownBy(() -> store.require(4242L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown conversation");
    }
}
