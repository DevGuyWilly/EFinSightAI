package ai.efinsight.e_finsight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;

    // Manual no-arg constructor (Lombok @NoArgsConstructor should generate this, but adding manually as workaround)
    // Hibernate/Jackson requires a no-arg constructor for instantiation
    public ChangePasswordRequest() {
    }

    // Manual getters and setters (Lombok @Data should generate these, but adding manually as workaround)
    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
