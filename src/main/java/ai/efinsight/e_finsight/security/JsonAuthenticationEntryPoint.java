package ai.efinsight.e_finsight.security;

import ai.efinsight.e_finsight.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * What an unauthenticated request to a protected route gets. With no entry point configured, Spring Security
 * answers such a request with 403 (Forbidden), which clients can't tell apart from "signed in but not allowed".
 * 401 + WWW-Authenticate is the correct status for a missing, expired or invalid token, and the body keeps the
 * same {message, timestamp} shape as every other error this API returns.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("Authentication required"));
    }
}
