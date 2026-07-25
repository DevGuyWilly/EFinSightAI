package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.llm.LLMAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AdkSynthesizer {
    private static final Logger log = LoggerFactory.getLogger(AdkSynthesizer.class);

    private final LLMAgent llmAgent;

    public AdkSynthesizer(LLMAgent llmAgent) {
        this.llmAgent = llmAgent;
    }

    public String synthesize(String query, List<Map<String, Object>> agentOutputs) {
        // Build a simple synthesis prompt that asks the LLM to merge sections
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert financial synthesizer. Merge the following agent outputs into a coherent financial plan. Be concise and provide recommendations.\\n\\n");
        sb.append("User question: ").append(query).append("\\n\\n");

        for (int i = 0; i < agentOutputs.size(); i++) {
            Map<String, Object> out = agentOutputs.get(i);
            sb.append("--- Agent Output " + (i+1) + " ---\\n");
            for (Map.Entry<String, Object> e : out.entrySet()) {
                sb.append(e.getKey()).append(":\\n");
                sb.append(e.getValue()).append("\\n\\n");
            }
        }

        String systemPrompt = "You are a synthesizer. Produce a markdown financial plan with sections and actionable recommendations.";
        try {
            String response = llmAgent.generateResponse(systemPrompt, sb.toString());
            return response;
        } catch (Exception e) {
            log.error("Synthesis failed", e);
            // Fallback: concatenate
            return agentOutputs.stream()
                .flatMap(m -> m.entrySet().stream())
                .map(e -> e.getKey() + "\n" + e.getValue())
                .collect(Collectors.joining("\n\n"));
        }
    }
}
