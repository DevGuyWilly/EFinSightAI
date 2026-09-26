package ai.efinsight.e_finsight.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets stored in the database (TrueLayer access/refresh tokens).
 *
 * Stored form: "enc:v1:" + base64url(12-byte random IV || ciphertext+tag). Values without the prefix are legacy
 * plaintext and are returned as-is, so existing rows keep working until TokenEncryptionMigrator encrypts them.
 *
 * The key comes from TOKEN_ENCRYPTION_KEY (environment variable or property). Every app instance that shares the
 * database needs the same value, or tokens encrypted by one can't be read by another.
 */
@Component
public class TokenCipher {
    static final String PREFIX = "enc:v1:";
    static final int MIN_SECRET_LENGTH = 32;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${TOKEN_ENCRYPTION_KEY:}") String secret) {
        if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "TOKEN_ENCRYPTION_KEY must be set to a random secret of at least " + MIN_SECRET_LENGTH
                            + " characters (e.g. `openssl rand -base64 32`). It encrypts stored TrueLayer tokens, "
                            + "so use the same value everywhere the app shares this database, and keep it: "
                            + "changing it makes existing tokens unreadable.");
        }
        // Hash the secret to exactly 256 bits, so any sufficiently random string works as the key material
        this.key = new SecretKeySpec(sha256(secret.strip()), "AES");
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt token", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored; // null, or legacy plaintext written before encryption was added
        }
        try {
            byte[] data = Base64.getUrlDecoder().decode(stored.substring(PREFIX.length()));
            if (data.length <= IV_BYTES) {
                throw new IllegalStateException("Stored token is truncated");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Stored token could not be decrypted: it was encrypted with a different "
                    + "TOKEN_ENCRYPTION_KEY, or has been modified", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Stored token could not be decrypted", e);
        }
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
