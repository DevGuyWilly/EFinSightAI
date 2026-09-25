package ai.efinsight.e_finsight.adk;

// The ADK agents could not produce an answer (model/API error, timeout, or no final response)
public class PlanGenerationException extends RuntimeException {
    public PlanGenerationException(String message) {
        super(message);
    }

    public PlanGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
