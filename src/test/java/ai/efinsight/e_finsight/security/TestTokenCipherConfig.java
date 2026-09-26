package ai.efinsight.e_finsight.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

// JPA slice tests build the whole entity model, including UserToken's encrypted columns, so they need a TokenCipher
@TestConfiguration
public class TestTokenCipherConfig {
    public static final String TEST_KEY = "test-only-token-encryption-key-0123456789";

    @Bean
    public TokenCipher tokenCipher() {
        return new TokenCipher(TEST_KEY);
    }
}
