package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.agent.AgentCoordinatorService;
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
    public ResponseEntity<Map<String, Object>> generatePlan(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        // Get the question from the request
        String question = request.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Question is required");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Log the request for debugging
        log.info("Generating plan for user: {} with question: {}", userId, question);
        
        try {
            // 
            AgentCoordinatorService.PlanResponse planResponse = coordinatorService.generatePlan(userId, question);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("plan", planResponse.getPlan());
            response.put("citations", planResponse.getCitations());
            response.put("question", question);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating plan for user: {}", userId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to generate plan: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

