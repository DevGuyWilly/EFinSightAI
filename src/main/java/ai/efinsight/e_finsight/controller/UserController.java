package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.dto.ChangePasswordRequest;
import ai.efinsight.e_finsight.dto.ErrorResponse;
import ai.efinsight.e_finsight.model.User;
import ai.efinsight.e_finsight.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Not under /api/auth/**: that prefix is permitAll in SecurityConfig, and this route needs the caller's JWT
// to know whose password to change.
@RestController
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            ErrorResponse error = new ErrorResponse("Current password is incorrect");
            error.setErrors(List.of(new ErrorResponse.FieldError("currentPassword", "Current password is incorrect")));
            return ResponseEntity.badRequest().body(error);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            ErrorResponse error = new ErrorResponse("New password must be different from the current password");
            error.setErrors(List.of(new ErrorResponse.FieldError("newPassword", "New password must be different from the current password")));
            return ResponseEntity.badRequest().body(error);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user ID: {}", userId);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
