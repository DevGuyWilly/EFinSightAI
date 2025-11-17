package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.dto.AuthResponse;
import ai.efinsight.e_finsight.dto.ErrorResponse;
import ai.efinsight.e_finsight.dto.LoginRequest;
import ai.efinsight.e_finsight.dto.SignupRequest;
import ai.efinsight.e_finsight.model.User;
import ai.efinsight.e_finsight.repository.UserRepository;
import ai.efinsight.e_finsight.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping(value = "/signup", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request){
//        Check User Repository is user exist
        if (userRepository.existsByEmail(request.getEmail())){
            ErrorResponse error = new ErrorResponse("Email already exists");
            return ResponseEntity.badRequest().body(error);
        }
//        Else, create new object and save to repository
        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFirstName(),
                request.getLastName()
        );

        userRepository.save(user);

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());

        return  ResponseEntity.ok(new AuthResponse(token, user));
    }


    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request){

        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null) {
            ErrorResponse error = new ErrorResponse("Invalid email or password");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(error);
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            ErrorResponse error = new ErrorResponse("Invalid email or password");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(error);
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());

        return ResponseEntity.ok(new AuthResponse(token, user));
    }
}
