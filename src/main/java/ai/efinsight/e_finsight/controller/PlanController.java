package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.adk.PlanGenerationException;
import ai.efinsight.e_finsight.agent.AgentCoordinatorService;
import ai.efinsight.e_finsight.dto.PlanRequest;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.exception.ConversationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plan")
public class PlanController {
    private static final Logger log = LoggerFactory.getLogger(PlanController.class);

    private final AgentCoordinatorService coordinatorService;

    public PlanController(AgentCoordinatorService coordinatorService) {
        this.coordinatorService = coordinatorService;
    }

    // Questions are stored and replayed as conversation history, so keep them to a sensible size
    static final int MAX_QUESTION_LENGTH = 2000;

    @PostMapping
    public ResponseEntity<?> generatePlan(
            @RequestBody PlanRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        String question = request.question();
        if (question == null || question.trim().isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Question is required");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            return errorResponse(HttpStatus.BAD_REQUEST,
                    "Question is too long (maximum " + MAX_QUESTION_LENGTH + " characters)");
        }
        question = question.strip();

        log.info("Generating plan for user: {} in conversation: {} with question: {}",
                userId, request.conversationId(), question);

        // Details are logged; clients get a generic message so internal/upstream error text isn't exposed
        try {
            PlanResponseDto planResponse = coordinatorService.generateStructuredPlan(userId, question, request.conversationId());
            return ResponseEntity.ok(planResponse);
        } catch (ConversationNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "Conversation not found");
        } catch (PlanGenerationException e) {
            log.error("AI agents failed to generate plan for user: {}", userId, e);
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "The assistant couldn't answer right now. Please try again in a moment.");
        } catch (Exception e) {
            log.error("Error generating plan for user: {}", userId, e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate plan.");
        }
    }

    private static ResponseEntity<PlanResponseDto> errorResponse(HttpStatus status, String message) {
        PlanResponseDto error = new PlanResponseDto();
        error.setSuccess(false);
        error.setError(message);
        return ResponseEntity.status(status).body(error);
    }
}

