package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.adk.AdkCoordinatorAgentNative;
import ai.efinsight.e_finsight.adk.QuestionRewriter;
import ai.efinsight.e_finsight.dto.CitationDto;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.model.Conversation;
import ai.efinsight.e_finsight.model.ConversationMessage;
import ai.efinsight.e_finsight.model.Transaction;
import ai.efinsight.e_finsight.rag.RagService;
import ai.efinsight.e_finsight.repository.TransactionRepository;
import ai.efinsight.e_finsight.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentCoordinatorService {
    private static final Logger log = LoggerFactory.getLogger(AgentCoordinatorService.class);

    // Earlier turns given to the agents as conversation history (10 questions and their answers)
    static final int HISTORY_MESSAGES = 20;
    // Long earlier answers are cut to this many characters in the history to bound prompt size
    static final int MAX_HISTORY_ANSWER_CHARS = 1500;

    private final AdkCoordinatorAgentNative adkCoordinatorAgent;
    private final QuestionRewriter questionRewriter;
    private final ConversationService conversationService;
    private final RagService ragService;
    private final TransactionRepository transactionRepository;

    // Pattern to parse transaction text: "Transaction: MERCHANT | Amount: -5.00 GBP | Category: PURCHASE | Date: 2025-11-14T00:00:00Z"
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
        "Transaction: ([^|]+) \\| Amount: ([^|]+) \\| Category: ([^|]+) \\| Date: (.+)"
    );

    public AgentCoordinatorService(
            AdkCoordinatorAgentNative adkCoordinatorAgent,
            QuestionRewriter questionRewriter,
            ConversationService conversationService,
            RagService ragService,
            TransactionRepository transactionRepository) {
        this.adkCoordinatorAgent = adkCoordinatorAgent;
        this.questionRewriter = questionRewriter;
        this.conversationService = conversationService;
        this.ragService = ragService;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Answers a question within a conversation and saves the turn. With a null conversationId a new conversation
     * is created (only once the answer succeeds); otherwise it must belong to the user, or
     * ConversationNotFoundException is thrown before any AI work is done.
     */
    public PlanResponseDto generateStructuredPlan(Long userId, String query, Long conversationId) {
        Conversation conversation = conversationId != null
            ? conversationService.requireOwned(userId, conversationId)
            : null;
        List<ConversationMessage> history = conversation != null
            ? conversationService.recentMessages(conversation.getId(), HISTORY_MESSAGES)
            : List.of();

        PlanExecution execution = executePlan(userId, query, history);
        Map<String, String> agentResponses = execution.result().agentResponses();

        PlanResponseDto.PlanSections sections = new PlanResponseDto.PlanSections(
            agentResponses.get("spending_analysis"),
            agentResponses.get("budget_plan"),
            agentResponses.get("investment_advice")
        );

        PlanResponseDto response = new PlanResponseDto(true, query, execution.result().summary(), sections, execution.citations(), agentResponses);
        Conversation saved = conversationService.recordTurn(userId, conversation, query, response);
        response.setConversationId(saved.getId());
        response.setConversationTitle(saved.getTitle());
        return response;
    }

    // Retrieve RAG context once, then run the ADK root coordinator agent, which decides which specialist(s) to delegate to
    private PlanExecution executePlan(Long userId, String query, List<ConversationMessage> history) {
        log.info("Running ADK coordinator for user: {} with query: {} ({} earlier messages)", userId, query, history.size());

        String historyText = formatHistory(history);
        // A follow-up ("what about last month?") embedded on its own retrieves the wrong transactions
        String retrievalQuery = history.isEmpty() ? query : questionRewriter.rewrite(userId, historyText, query);

        // RAG supplies a top-15 sample of specific, relevant transactions; totals, rankings and trends over the full
        // history come from the specialists' TransactionAnalyticsTools instead, so topK doesn't need to scale with it
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, retrievalQuery, 15);
        List<CitationDto> citations = buildStructuredCitations(contexts);
        String contextText = ragService.buildContextString(contexts);

        // Throws PlanGenerationException on failure, so a failed run is never returned as a successful plan
        AdkCoordinatorAgentNative.CoordinatorResult result = adkCoordinatorAgent.run(userId, query, contextText, historyText);

        return new PlanExecution(result, citations);
    }

    private record PlanExecution(AdkCoordinatorAgentNative.CoordinatorResult result, List<CitationDto> citations) {
    }

    static String formatHistory(List<ConversationMessage> history) {
        if (history.isEmpty()) {
            return AdkCoordinatorAgentNative.NO_HISTORY;
        }
        StringBuilder sb = new StringBuilder();
        for (ConversationMessage message : history) {
            boolean user = message.getRole() == ConversationMessage.Role.USER;
            String content = message.getContent();
            if (!user && content.length() > MAX_HISTORY_ANSWER_CHARS) {
                content = content.substring(0, MAX_HISTORY_ANSWER_CHARS) + "…";
            }
            sb.append(user ? "User: " : "Assistant: ").append(content).append("\n\n");
        }
        return sb.toString().strip();
    }

    // Build structured citations from RAG contexts
    private List<CitationDto> buildStructuredCitations(List<RagService.RagContext> contexts) {
        List<CitationDto> citations = new ArrayList<>();
        
        for (RagService.RagContext ctx : contexts) {
            CitationDto citation = new CitationDto();
            citation.setTransactionId(ctx.sourceId);
            
            // Try to fetch actual transaction for accurate data
            Transaction transaction = transactionRepository.findById(ctx.sourceId).orElse(null);
            
            if (transaction != null) {
                // Use actual transaction data
                citation.setMerchant(transaction.getMerchantName());
                citation.setAmount(transaction.getAmount() != null ? transaction.getAmount().toString() : null);
                citation.setCurrency(transaction.getCurrency());
                citation.setCategory(transaction.getTransactionCategory());
                citation.setDate(transaction.getTimestamp() != null ? 
                    transaction.getTimestamp().toString() : null);
                citation.setDescription(transaction.getDescription());
            } else {
                // Fallback: Parse from chunk text
                parseCitationFromText(ctx.text, citation);
            }
            
            citations.add(citation);
        }
        
        return citations;
    }

    // Parse transaction details from chunk text
    private void parseCitationFromText(String text, CitationDto citation) {
        Matcher matcher = TRANSACTION_PATTERN.matcher(text);
        if (matcher.find()) {
            citation.setMerchant(matcher.group(1).trim());
            String amountStr = matcher.group(2).trim();
            // Extract amount and currency
            String[] amountParts = amountStr.split("\\s+");
            if (amountParts.length >= 2) {
                citation.setAmount(amountParts[0]);
                citation.setCurrency(amountParts[1]);
            } else {
                citation.setAmount(amountStr);
            }
            citation.setCategory(matcher.group(3).trim());
            citation.setDate(matcher.group(4).trim());
            citation.setDescription(matcher.group(1).trim());
        } else {
            // Fallback: use full text as description
            citation.setDescription(text);
        }
    }

}

