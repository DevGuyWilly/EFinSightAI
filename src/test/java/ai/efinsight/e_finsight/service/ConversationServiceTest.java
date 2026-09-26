package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.security.TestTokenCipherConfig;
import ai.efinsight.e_finsight.dto.CitationDto;
import ai.efinsight.e_finsight.dto.ConversationDetailDto;
import ai.efinsight.e_finsight.dto.ConversationSummaryDto;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.exception.ConversationNotFoundException;
import ai.efinsight.e_finsight.model.Conversation;
import ai.efinsight.e_finsight.model.ConversationMessage;
import ai.efinsight.e_finsight.repository.ConversationMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs on in-memory H2 by default; set TEST_DB_URL/USERNAME/PASSWORD/DRIVER/DIALECT to run against real Postgres
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:conversations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS vector AS VARCHAR}",
        "spring.datasource.username=${TEST_DB_USERNAME:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
        "spring.jpa.database-platform=${TEST_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.properties.hibernate.dialect=${TEST_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({ConversationService.class, TestTokenCipherConfig.class})
class ConversationServiceTest {
    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationMessageRepository messageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void firstTurnCreatesAConversationTitledAfterTheQuestion() {
        Conversation conversation = conversationService.recordTurn(USER, null, "Where do I spend the most?", answer("TESCO"));

        assertThat(conversation.getId()).isNotNull();
        assertThat(conversation.getTitle()).isEqualTo("Where do I spend the most?");
        assertThat(conversationService.list(USER)).singleElement()
                .satisfies(summary -> assertThat(summary.messageCount()).isEqualTo(2));
    }

    @Test
    void detailReplaysEveryTurnWithTheFullAnswer() {
        Conversation conversation = conversationService.recordTurn(USER, null, "Where do I spend the most?", answer("TESCO"));
        conversationService.recordTurn(USER, conversation, "What about August?", answer("AMAZON"));
        flushAndClear();

        ConversationDetailDto detail = conversationService.get(USER, conversation.getId());

        assertThat(detail.messages()).extracting(m -> m.role() + ": " + m.content()).containsExactly(
                "user: Where do I spend the most?",
                "assistant: Most at TESCO.",
                "user: What about August?",
                "assistant: Most at AMAZON.");
        PlanResponseDto second = detail.messages().get(3).response();
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.getQuestion()).isEqualTo("What about August?");
        assertThat(second.getConversationId()).isEqualTo(conversation.getId());
        assertThat(second.getSections().getSpendingAnalysis()).isEqualTo("AMAZON analysis");
        assertThat(second.getCitations()).singleElement()
                .satisfies(c -> assertThat(c.getMerchant()).isEqualTo("AMAZON"));
        assertThat(second.getAgentResponses()).containsEntry("spending_analysis", "AMAZON analysis");
        assertThat(detail.messages().get(2).response()).isNull();
    }

    @Test
    void listsMostRecentlyUsedConversationsFirst() {
        Conversation older = conversationService.recordTurn(USER, null, "First chat", answer("A"));
        Conversation newer = conversationService.recordTurn(USER, null, "Second chat", answer("B"));
        conversationService.recordTurn(USER, older, "Follow-up in the first chat", answer("C"));
        conversationService.recordTurn(OTHER_USER, null, "Someone else's chat", answer("D"));

        assertThat(conversationService.list(USER)).extracting(ConversationSummaryDto::id)
                .containsExactly(older.getId(), newer.getId());
    }

    @Test
    void recentMessagesAreTheLatestInChronologicalOrder() {
        Conversation conversation = conversationService.recordTurn(USER, null, "Q1", answer("A1"));
        conversationService.recordTurn(USER, conversation, "Q2", answer("A2"));
        conversationService.recordTurn(USER, conversation, "Q3", answer("A3"));

        List<ConversationMessage> recent = conversationService.recentMessages(conversation.getId(), 4);

        assertThat(recent).extracting(ConversationMessage::getContent)
                .containsExactly("Q2", "Most at A2.", "Q3", "Most at A3.");
    }

    @Test
    void anotherUsersConversationBehavesAsIfItDoesNotExist() {
        Conversation theirs = conversationService.recordTurn(OTHER_USER, null, "Private question", answer("X"));
        Long id = theirs.getId();

        assertThatThrownBy(() -> conversationService.get(USER, id)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> conversationService.delete(USER, id)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> conversationService.recordTurn(USER, theirs, "Sneaky follow-up", answer("Y")))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThat(conversationService.list(USER)).isEmpty();
    }

    @Test
    void deletingAConversationDeletesItsMessages() {
        Conversation keep = conversationService.recordTurn(USER, null, "Keep me", answer("A"));
        Conversation remove = conversationService.recordTurn(USER, null, "Delete me", answer("B"));
        flushAndClear();

        conversationService.delete(USER, remove.getId());
        flushAndClear();

        assertThat(conversationService.list(USER)).extracting(ConversationSummaryDto::id).containsExactly(keep.getId());
        assertThat(messageRepository.count()).isEqualTo(2);
    }

    @Test
    void clearingHistoryOnlyAffectsThatUser() {
        conversationService.recordTurn(USER, null, "Mine 1", answer("A"));
        conversationService.recordTurn(USER, null, "Mine 2", answer("B"));
        conversationService.recordTurn(OTHER_USER, null, "Theirs", answer("C"));
        flushAndClear();

        assertThat(conversationService.deleteAll(USER)).isEqualTo(2);
        flushAndClear();

        assertThat(conversationService.list(USER)).isEmpty();
        assertThat(conversationService.list(OTHER_USER)).hasSize(1);
        assertThat(messageRepository.count()).isEqualTo(2);
    }

    @Test
    void titlesAreTheFirstLineCutAtAWordBoundary() {
        assertThat(ConversationService.titleFrom("  How much on\n  groceries?  ")).isEqualTo("How much on");
        assertThat(ConversationService.titleFrom("   ")).isEqualTo("New conversation");
        String longQuestion = "Could you break down my spending on eating out and takeaways across the last three months please";
        String title = ConversationService.titleFrom(longQuestion);
        assertThat(title).hasSizeLessThanOrEqualTo(80).endsWith("…").doesNotContain("  ");
        assertThat(longQuestion).startsWith(title.substring(0, title.length() - 1));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PlanResponseDto answer(String merchant) {
        CitationDto citation = new CitationDto();
        citation.setTransactionId(1L);
        citation.setMerchant(merchant);
        return new PlanResponseDto(true, "question", "Most at " + merchant + ".",
                new PlanResponseDto.PlanSections(merchant + " analysis", null, null),
                List.of(citation), Map.of("spending_analysis", merchant + " analysis"));
    }
}
