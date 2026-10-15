package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.dto.ErrorResponse;
import ai.efinsight.e_finsight.dto.MeResponse;
import ai.efinsight.e_finsight.model.User;
import ai.efinsight.e_finsight.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final UserRepository userRepository;

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping(value = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            // A valid token for an account that no longer exists is an invalid session, not a missing page
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Account not found"));
        }
        return ResponseEntity.ok(MeResponse.from(user));
    }
}
