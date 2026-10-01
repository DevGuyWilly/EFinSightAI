package ai.efinsight.e_finsight.model;

import jakarta.persistence.*;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    // Hashed with BCrypt
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private boolean bankConnected = false;

    // User preference: hides money figures on the dashboard. Synced across devices via login/signup response
    // and POST /api/users/preferences — there's no live push, so an already-open session on another device
    // picks it up on its next login.
    // columnDefinition carries an explicit DEFAULT: ddl-auto=update's plain "not null" ALTER TABLE ADD COLUMN
    // fails on a table that already has rows (Postgres has nothing to put in them), whereas "not null default
    // false" backfills existing rows as part of the same statement.
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean hideBalances = false;

    @PrePersist
    protected void onCreate(){
        createdAt = LocalDateTime.now();
    }

    // Manual no-arg constructor (Lombok @NoArgsConstructor should generate this, but adding manually as workaround)
    // Hibernate requires a no-arg constructor for entity instantiation
    public User() {
    }

    public User(String email, String password, String firstName, String lastName){
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // Manual getters and setters (Lombok @Data should generate these, but adding manually as workaround)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isBankConnected() {
        return bankConnected;
    }

    public void setBankConnected(boolean bankConnected) {
        this.bankConnected = bankConnected;
    }

    public boolean isHideBalances() {
        return hideBalances;
    }

    public void setHideBalances(boolean hideBalances) {
        this.hideBalances = hideBalances;
    }
}
