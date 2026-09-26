package ai.efinsight.e_finsight.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Encrypts TrueLayer tokens that were stored as plaintext before TokenCipher existed. Runs on every startup and
 * only touches rows still in plaintext, so it's a no-op once everything is encrypted. Works on raw column values:
 * going through the entity wouldn't write anything, since the decrypted value looks unchanged to Hibernate.
 */
@Component
public class TokenEncryptionMigrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TokenEncryptionMigrator.class);

    private final JdbcTemplate jdbc;
    private final TokenCipher tokenCipher;

    public TokenEncryptionMigrator(JdbcTemplate jdbc, TokenCipher tokenCipher) {
        this.jdbc = jdbc;
        this.tokenCipher = tokenCipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        encryptLegacyTokens();
    }

    // Returns how many rows were encrypted
    public int encryptLegacyTokens() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, access_token, refresh_token FROM user_tokens
                WHERE (access_token IS NOT NULL AND access_token NOT LIKE 'enc:v1:%')
                   OR (refresh_token IS NOT NULL AND refresh_token NOT LIKE 'enc:v1:%')
                """);
        for (Map<String, Object> row : rows) {
            jdbc.update("UPDATE user_tokens SET access_token = ?, refresh_token = ? WHERE id = ?",
                    encryptIfPlain((String) row.get("access_token")),
                    encryptIfPlain((String) row.get("refresh_token")),
                    row.get("id"));
        }
        if (!rows.isEmpty()) {
            log.info("Encrypted TrueLayer tokens stored as plaintext for {} user(s)", rows.size());
        }
        return rows.size();
    }

    private String encryptIfPlain(String value) {
        return value == null || TokenCipher.isEncrypted(value) ? value : tokenCipher.encrypt(value);
    }
}
