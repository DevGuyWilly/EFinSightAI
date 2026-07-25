package ai.efinsight.e_finsight.adk;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Agent {
    // Run the agent for a user and query. Returns a map of structured outputs.
    CompletableFuture<Map<String, Object>> run(Long userId, String query);
}
