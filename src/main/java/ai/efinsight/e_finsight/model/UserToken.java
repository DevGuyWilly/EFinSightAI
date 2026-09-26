package ai.efinsight.e_finsight.model;

import ai.efinsight.e_finsight.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_tokens")
public class UserToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String userId;

    // Encrypted at rest (AES-GCM, see TokenCipher); the entity always holds the plaintext
    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String refreshToken;

    private LocalDateTime consentCreatedAt;
    private Integer expiresIn;
    private LocalDateTime lastRefreshedAt;

    @Column(name = "consent_expires_at")
    private LocalDateTime consentExpiresAt; // 90 days

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public LocalDateTime getConsentCreatedAt() {
        return consentCreatedAt;
    }

    public void setConsentCreatedAt(LocalDateTime consentCreatedAt) {
        this.consentCreatedAt = consentCreatedAt;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    public LocalDateTime getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public void setLastRefreshedAt(LocalDateTime lastRefreshedAt) {
        this.lastRefreshedAt = lastRefreshedAt;
    }

    public LocalDateTime getConsentExpiresAt() {
        return consentExpiresAt;
    }

    public void setConsentExpiresAt(LocalDateTime consentExpiresAt) {
        this.consentExpiresAt = consentExpiresAt;
    }
}
