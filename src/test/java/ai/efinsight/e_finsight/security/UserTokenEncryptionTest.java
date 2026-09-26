package ai.efinsight.e_finsight.security;

import ai.efinsight.e_finsight.model.UserToken;
import ai.efinsight.e_finsight.repository.UserTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Runs on in-memory H2 by default; set TEST_DB_URL/USERNAME/PASSWORD/DRIVER/DIALECT to run against real Postgres
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:tokens;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS vector AS VARCHAR}",
        "spring.datasource.username=${TEST_DB_USERNAME:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
        "spring.jpa.database-platform=${TEST_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.properties.hibernate.dialect=${TEST_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({TestTokenCipherConfig.class, TokenEncryptionMigrator.class})
class UserTokenEncryptionTest {
    @Autowired
    private UserTokenRepository tokenRepository;

    @Autowired
    private TokenEncryptionMigrator migrator;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void clear() {
        tokenRepository.deleteAll();
    }

    @Test
    void tokensAreEncryptedInTheDatabaseButPlainInTheEntity() {
        tokenRepository.saveTokens("1", "access-123", "refresh-456", LocalDateTime.now(), 3600);
        flushAndClear();

        Map<String, Object> raw = rawRow("1");
        assertThat((String) raw.get("access_token")).startsWith("enc:v1:").doesNotContain("access-123");
        assertThat((String) raw.get("refresh_token")).startsWith("enc:v1:").doesNotContain("refresh-456");

        UserToken token = tokenRepository.findByUserId("1").orElseThrow();
        assertThat(token.getAccessToken()).isEqualTo("access-123");
        assertThat(token.getRefreshToken()).isEqualTo("refresh-456");
    }

    @Test
    void migratorEncryptsLegacyPlaintextRowsOnce() {
        jdbc.update("INSERT INTO user_tokens (user_id, access_token, refresh_token) VALUES ('2', 'plain-access', 'plain-refresh')");
        jdbc.update("INSERT INTO user_tokens (user_id, access_token, refresh_token) VALUES ('3', 'plain-access-only', NULL)");

        assertThat(migrator.encryptLegacyTokens()).isEqualTo(2);
        assertThat(migrator.encryptLegacyTokens()).isZero();

        assertThat((String) rawRow("2").get("access_token")).startsWith("enc:v1:");
        assertThat((String) rawRow("2").get("refresh_token")).startsWith("enc:v1:");
        assertThat(rawRow("3").get("refresh_token")).isNull();
        flushAndClear();
        assertThat(tokenRepository.findByUserId("2").orElseThrow().getRefreshToken()).isEqualTo("plain-refresh");
        assertThat(tokenRepository.findByUserId("3").orElseThrow().getAccessToken()).isEqualTo("plain-access-only");
    }

    @Test
    void legacyPlaintextIsStillReadableBeforeMigration() {
        jdbc.update("INSERT INTO user_tokens (user_id, access_token, refresh_token) VALUES ('4', 'old-access', 'old-refresh')");
        flushAndClear();

        assertThat(tokenRepository.findByUserId("4").orElseThrow().getAccessToken()).isEqualTo("old-access");
    }

    private Map<String, Object> rawRow(String userId) {
        return jdbc.queryForMap("SELECT access_token, refresh_token FROM user_tokens WHERE user_id = ?", userId);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
