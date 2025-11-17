package ai.efinsight.e_finsight.repository;

import ai.efinsight.e_finsight.model.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByUserId(String userId);

    default void saveTokens(String userId, String accessToken,
                            String refreshToken, LocalDateTime consentCreatedAt,
                            Integer expiresIn) {
        UserToken token = findByUserId(userId).orElse(new UserToken());
        token.setUserId(userId);
        token.setAccessToken(accessToken); // TODO: Encrypt
        token.setRefreshToken(refreshToken); // TODO: Encrypt
        token.setConsentCreatedAt(consentCreatedAt);
        token.setExpiresIn(expiresIn);
        token.setLastRefreshedAt(LocalDateTime.now());
        token.setConsentExpiresAt(consentCreatedAt.plusDays(90));
        save(token);
    }
}
