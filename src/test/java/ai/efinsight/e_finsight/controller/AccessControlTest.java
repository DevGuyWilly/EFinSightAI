package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.config.SecurityConfig;
import ai.efinsight.e_finsight.config.TrueLayerConfig;
import ai.efinsight.e_finsight.model.User;
import ai.efinsight.e_finsight.repository.UserRepository;
import ai.efinsight.e_finsight.security.JsonAuthenticationEntryPoint;
import ai.efinsight.e_finsight.security.JwtAuthenticationFilter;
import ai.efinsight.e_finsight.service.ConnectTicketService;
import ai.efinsight.e_finsight.service.TrueLayerAuthService;
import ai.efinsight.e_finsight.util.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real SecurityConfig and JWT filter in front of the account and bank-connect routes, so it checks what a
 * client actually sees (status codes, headers) and not just what each class does on its own. Web slice only: no
 * database, and nothing here reads application.properties.
 */
@WebMvcTest(controllers = {MeController.class, BankController.class, TrueLayerAuthController.class})
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, JwtUtil.class,
        ConnectTicketService.class, TrueLayerConfig.class})
// Test properties outrank application.properties, so these hold whatever a developer has in their local copy.
// (TrueLayerConfig has to be imported rather than built by hand: @ConfigurationProperties rebinds any instance.)
@TestPropertySource(properties = {
        "jwt.secret=test-jwt-secret-that-is-long-enough-for-hs256",
        "jwt.expiration=3600000",
        "truelayer.client-id=test-client",
        "truelayer.redirect-uri=https://app.example/callback"
})
class AccessControlTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ConnectTicketService connectTicketService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private TrueLayerAuthService trueLayerAuthService;

    private String bearer(long userId) {
        return "Bearer " + jwtUtil.generateToken("user@example.com", userId);
    }

    // ---- 401, not 403, for a missing or bad token ----

    @Test
    void aProtectedRouteWithNoTokenIs401WithABearerChallengeAndAJsonBody() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("application/json")))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void aGarbageOrExpiredBearerTokenIs401() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aConnectTicketIsNotAValidBearerToken() throws Exception {
        String ticket = connectTicketService.issue(1L);

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyProtectedRouteBehavesTheSame() throws Exception {
        mockMvc.perform(post("/api/bank/connect-url")).andExpect(status().isUnauthorized());
    }

    // ---- GET /api/me ----

    @Test
    void meReturnsTheCurrentUserWithoutTheirPassword() throws Exception {
        User user = new User("jamie@example.com", "bcrypt-hash", "Jamie", "Carter");
        user.setId(1L);
        user.setBankConnected(true);
        user.setHideBalances(true);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 3, 10, 0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("jamie@example.com"))
                .andExpect(jsonPath("$.firstName").value("Jamie"))
                .andExpect(jsonPath("$.lastName").value("Carter"))
                .andExpect(jsonPath("$.bankConnected").value(true))
                .andExpect(jsonPath("$.hideBalances").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-09-03T10:00:00"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void meForAValidTokenWhoseAccountNoLongerExistsIs401() throws Exception {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(9L)))
                .andExpect(status().isUnauthorized());
    }

    // ---- bank connection: ticket instead of the login JWT in the URL ----

    @Test
    void aSignedInUserGetsAConnectUrlThatStartsTheFlowOnceAndWithoutAnyHeader() throws Exception {
        when(trueLayerAuthService.generateAndStoreState("7")).thenReturn("signed-state");

        MvcResult issued = mockMvc.perform(post("/api/bank/connect-url").header(HttpHeaders.AUTHORIZATION, bearer(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/auth/connect-bank?ticket=")))
                .andReturn();
        String url = JsonPath.read(issued.getResponse().getContentAsString(), "$.url");

        // A plain browser navigation: no Authorization header, and no login JWT anywhere in the url
        mockMvc.perform(get(url))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://auth.truelayer.com/?response_type=code&client_id=test-client")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("state=signed-state")));
        verify(trueLayerAuthService).generateAndStoreState("7");

        // Replaying the same url goes nowhere
        mockMvc.perform(get(url))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/auth/error?message=invalid_token"));
    }

    @Test
    void theOldTokenParameterIsGoneEvenWithAValidLoginJwt() throws Exception {
        mockMvc.perform(get("/auth/connect-bank").param("token", jwtUtil.generateToken("user@example.com", 7L)))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/auth/error?message=invalid_token"));
        verify(trueLayerAuthService, never()).generateAndStoreState(any());
    }

    @Test
    void aMissingOrInvalidTicketRedirectsToTheErrorPage() throws Exception {
        mockMvc.perform(get("/auth/connect-bank"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/auth/error?message=invalid_token"));
        mockMvc.perform(get("/auth/connect-bank").param("ticket", "not-a-ticket"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/auth/error?message=invalid_token"));
    }
}
