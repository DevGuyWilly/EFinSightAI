package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.adk.AdkCoordinatorAgentNative;
import ai.efinsight.e_finsight.dto.CitationDto;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.model.Transaction;
import ai.efinsight.e_finsight.rag.RagService;
import ai.efinsight.e_finsight.repository.TransactionRepository;
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

    private final AdkCoordinatorAgentNative adkCoordinatorAgent;
    private final RagService ragService;
    private final TransactionRepository transactionRepository;

    // Pattern to parse transaction text: "Transaction: MERCHANT | Amount: -5.00 GBP | Category: PURCHASE | Date: 2025-11-14T00:00:00Z"
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
        "Transaction: ([^|]+) \\| Amount: ([^|]+) \\| Category: ([^|]+) \\| Date: (.+)"
    );

    public AgentCoordinatorService(
            AdkCoordinatorAgentNative adkCoordinatorAgent,
            RagService ragService,
            TransactionRepository transactionRepository) {
        this.adkCoordinatorAgent = adkCoordinatorAgent;
        this.ragService = ragService;
        this.transactionRepository = transactionRepository;
    }

    // Generate a structured plan response DTO for the user
    public PlanResponseDto generateStructuredPlan(Long userId, String query) {
        PlanExecution execution = executePlan(userId, query);
        Map<String, String> agentResponses = execution.result().agentResponses();

        PlanResponseDto.PlanSections sections = new PlanResponseDto.PlanSections(
            agentResponses.get("spending_analysis"),
            agentResponses.get("budget_plan"),
            agentResponses.get("investment_advice")
        );

        return new PlanResponseDto(true, query, execution.result().summary(), sections, execution.citations(), agentResponses);
    }

    // Retrieve RAG context once, then run the ADK root coordinator agent, which decides which specialist(s) to delegate to
    private PlanExecution executePlan(Long userId, String query) {
        log.info("Running ADK coordinator for user: {} with query: {}", userId, query);

        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 15);
        List<CitationDto> citations = buildStructuredCitations(contexts);
        String contextText = ragService.buildContextString(contexts);

        AdkCoordinatorAgentNative.CoordinatorResult result;
        try {
            result = adkCoordinatorAgent.run(userId, query, contextText).get();
        } catch (Exception e) {
            log.error("Error running ADK coordinator", e);
            result = new AdkCoordinatorAgentNative.CoordinatorResult(
                "Unable to generate a plan at this time. Error: " + e.getMessage(), Map.of());
        }

        return new PlanExecution(result, citations);
    }

    private record PlanExecution(AdkCoordinatorAgentNative.CoordinatorResult result, List<CitationDto> citations) {
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

