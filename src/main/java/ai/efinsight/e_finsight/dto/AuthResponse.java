package ai.efinsight.e_finsight.dto;

import ai.efinsight.e_finsight.model.User;
import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String type = "Bearer";
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private boolean bankConnected;

    // No-arg constructor for Jackson serialization
    public AuthResponse() {
    }

    public AuthResponse(String token, User user) {
        this.token = token;
        this.userId = user.getId();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.bankConnected = user.isBankConnected();
    }

    // Manual getters and setters (Lombok @Data should generate these, but adding manually as workaround)
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isBankConnected() {
        return bankConnected;
    }

    public void setBankConnected(boolean bankConnected) {
        this.bankConnected = bankConnected;
    }
}
