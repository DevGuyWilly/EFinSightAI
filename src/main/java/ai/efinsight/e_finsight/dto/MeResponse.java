package ai.efinsight.e_finsight.dto;

import ai.efinsight.e_finsight.model.User;

import java.time.LocalDateTime;

/**
 * The signed-in user as the server has them right now. Same field names as the login/signup response (minus the
 * token), so a client can merge it straight into the user it got at login: that copy goes stale as soon as the
 * bank is connected or a preference is changed from another device.
 */
public record MeResponse(
        Long userId,
        String email,
        String firstName,
        String lastName,
        boolean bankConnected,
        boolean hideBalances,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt) {

    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isBankConnected(),
                user.isHideBalances(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
