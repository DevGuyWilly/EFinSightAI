package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.dto.ConversationDetailDto;
import ai.efinsight.e_finsight.dto.ConversationListResponse;
import ai.efinsight.e_finsight.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Advisor conversation history. New conversations and messages are created through POST /api/plan.
// A conversation that doesn't exist or belongs to another user is a 404 (see GlobalExceptionHandler).
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ConversationListResponse list(Authentication authentication) {
        return new ConversationListResponse(conversationService.list(userId(authentication)));
    }

    @GetMapping("/{id}")
    public ConversationDetailDto get(@PathVariable Long id, Authentication authentication) {
        return conversationService.get(userId(authentication), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        conversationService.delete(userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(Authentication authentication) {
        conversationService.deleteAll(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
