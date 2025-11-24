package ai.efinsight.e_finsight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanResponseDto {
    private boolean success;
    private String question;
    private String summary;
    private PlanSections sections;
    private List<CitationDto> citations;
    private Map<String, String> agentResponses;
    private String error;

    public PlanResponseDto() {
    }

    public PlanResponseDto(boolean success, String question, String summary, 
                          PlanSections sections, List<CitationDto> citations, 
                          Map<String, String> agentResponses) {
        this.success = success;
        this.question = question;
        this.summary = summary;
        this.sections = sections;
        this.citations = citations;
        this.agentResponses = agentResponses;
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public PlanSections getSections() {
        return sections;
    }

    public void setSections(PlanSections sections) {
        this.sections = sections;
    }

    public List<CitationDto> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationDto> citations) {
        this.citations = citations;
    }

    public Map<String, String> getAgentResponses() {
        return agentResponses;
    }

    public void setAgentResponses(Map<String, String> agentResponses) {
        this.agentResponses = agentResponses;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    // Inner class for organized sections
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanSections {
        private String spendingAnalysis;
        private String budgetRecommendations;
        private String investmentAdvice;

        public PlanSections() {
        }

        public PlanSections(String spendingAnalysis, String budgetRecommendations, String investmentAdvice) {
            this.spendingAnalysis = spendingAnalysis;
            this.budgetRecommendations = budgetRecommendations;
            this.investmentAdvice = investmentAdvice;
        }

        public String getSpendingAnalysis() {
            return spendingAnalysis;
        }

        public void setSpendingAnalysis(String spendingAnalysis) {
            this.spendingAnalysis = spendingAnalysis;
        }

        public String getBudgetRecommendations() {
            return budgetRecommendations;
        }

        public void setBudgetRecommendations(String budgetRecommendations) {
            this.budgetRecommendations = budgetRecommendations;
        }

        public String getInvestmentAdvice() {
            return investmentAdvice;
        }

        public void setInvestmentAdvice(String investmentAdvice) {
            this.investmentAdvice = investmentAdvice;
        }
    }
}

