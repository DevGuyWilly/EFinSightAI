package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.agent.AgentCoordinatorService;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/plan")
public class PlanController {
    private static final Logger log = LoggerFactory.getLogger(PlanController.class);

    private final AgentCoordinatorService coordinatorService;

    public PlanController(AgentCoordinatorService coordinatorService) {
        this.coordinatorService = coordinatorService;
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
        log.info("Generating plan for user: {} with question: {} (legacy: {})", 
            userId, question, legacy);
        
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
            } else {
                // Return structured response (default)
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

