package io.github.dreadvoice.incant.conversation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("select distinct m from Message m left join fetch m.skills "
            + "where m.conversation.id = :conversationId order by m.id")
    List<Message> findWithSkillsByConversationId(@Param("conversationId") Long conversationId);

    long countByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
