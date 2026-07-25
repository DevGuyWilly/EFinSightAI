package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.adk.AdkOrchestratorService;
import ai.efinsight.e_finsight.agent.AgentCoordinatorService;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.dto.PlanResponseDto.PlanSections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/plan")
public class PlanController {
    private static final Logger log = LoggerFactory.getLogger(PlanController.class);

    private final AgentCoordinatorService coordinatorService;
    private final AdkOrchestratorService orchestratorService;

    @Value("${feature.adk.enabled:false}")
    private boolean adkEnabled;

    public PlanController(AgentCoordinatorService coordinatorService, AdkOrchestratorService orchestratorService) {
        this.coordinatorService = coordinatorService;
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public ResponseEntity<?> generatePlan(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        // Get the question from the request
        String question = request.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            PlanResponseDto error = new PlanResponseDto();
            error.setSuccess(false);
            error.setError("Question is required");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Check if legacy format is requested (for backward compatibility)
        boolean legacy = request.containsKey("legacy") && 
                        Boolean.parseBoolean(request.get("legacy"));
        
        // Log the request for debugging
        log.info("Generating plan for user: {} with question: {} (legacy: {}, adkEnabled: {})", 
            userId, question, legacy, adkEnabled);
        
        try {
            if (legacy) {
                // Return legacy format for backward compatibility
                AgentCoordinatorService.PlanResponse planResponse = 
                    coordinatorService.generatePlan(userId, question);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("plan", planResponse.getPlan());
                response.put("citations", planResponse.getCitations());
                response.put("question", question);
                
                return ResponseEntity.ok(response);
            } else if (adkEnabled) {
                // Use new ADK-based orchestrator
                AdkOrchestratorService.PlanResult result = orchestratorService.generatePlan(userId, question);

                PlanResponseDto dto = new PlanResponseDto();
                dto.setSuccess(true);
                dto.setQuestion(question);
                String summary = result.plan != null && result.plan.length() > 200
                    ? result.plan.substring(0, 200) + "..."
                    : result.plan;
                dto.setSummary(summary);

                PlanSections sections = new PlanSections(
                    (String) result.agentResponses.getOrDefault("spending_analysis", null),
                    (String) result.agentResponses.getOrDefault("budget_plan", null),
                    (String) result.agentResponses.getOrDefault("investment_advice", null)
                );
                dto.setSections(sections);

                // Convert agentResponses values to strings
                Map<String, String> stringResponses = result.agentResponses.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == null ? null : e.getValue().toString()));
                dto.setAgentResponses(stringResponses);

                // Citations omitted for now (could be converted to CitationDto)
                return ResponseEntity.ok(dto);
            } else {
                // Return structured response (default legacy structured coordinator)
                PlanResponseDto planResponse = coordinatorService.generateStructuredPlan(userId, question);
                return ResponseEntity.ok(planResponse);
            }
        } catch (Exception e) {
            log.error("Error generating plan for user: {}", userId, e);
            PlanResponseDto error = new PlanResponseDto();
            error.setSuccess(false);
            error.setError("Failed to generate plan: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

