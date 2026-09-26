package ai.efinsight.e_finsight.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {
    private final TokenCipher cipher = new TokenCipher(TestTokenCipherConfig.TEST_KEY);

    @Test
    void roundTripsAndNeverStoresThePlaintext() {
        String token = "eyJhbGciOi.truelayer-access-token.sig";

        String stored = cipher.encrypt(token);

        assertThat(stored).startsWith("enc:v1:").doesNotContain(token);
        assertThat(cipher.decrypt(stored)).isEqualTo(token);
    }

    @Test
    void usesAFreshIvEachTime() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void passesNullAndLegacyPlaintextThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("legacy-plaintext-token")).isEqualTo("legacy-plaintext-token");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String stored = cipher.encrypt("token");
        char last = stored.charAt(stored.length() - 1);
        String tampered = stored.substring(0, stored.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explainsAKeyMismatch() {
        String stored = cipher.encrypt("token");
        TokenCipher other = new TokenCipher("a-completely-different-key-0123456789abcdef");

        assertThatThrownBy(() -> other.decrypt(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different TOKEN_ENCRYPTION_KEY");
    }

    @Test
    void refusesAMissingOrWeakKey() {
        assertThatThrownBy(() -> new TokenCipher("")).hasMessageContaining("TOKEN_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new TokenCipher("too-short")).hasMessageContaining("at least 32");
    }
}
