package ai.efinsight.e_finsight.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private LocalDateTime timestamp;
    private List<FieldError> errors;

    // Manual no-arg constructor (Lombok @NoArgsConstructor should generate this, but adding manually as workaround)
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String message) {
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // Manual getters and setters (Lombok @Data should generate these, but adding manually as workaround)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public void setErrors(List<FieldError> errors) {
        this.errors = errors;
    }

    @Data
    @NoArgsConstructor
    public static class FieldError {
        private String field;
        private String message;

        // Manual constructor (Lombok @AllArgsConstructor should generate this, but adding manually as workaround)
        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        // Manual getters and setters
        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

