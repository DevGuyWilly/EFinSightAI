package ai.efinsight.e_finsight.dto;

import lombok.Data;

// Boolean (not boolean): null means "leave unchanged", so new preferences can be added later and a client
// only has to send the one it's changing.
@Data
public class UpdatePreferencesRequest {

    private Boolean hideBalances;

    // Manual no-arg constructor (Lombok @NoArgsConstructor should generate this, but adding manually as workaround)
    public UpdatePreferencesRequest() {
    }

    // Manual getters and setters (Lombok @Data should generate these, but adding manually as workaround)
    public Boolean getHideBalances() {
        return hideBalances;
    }

    public void setHideBalances(Boolean hideBalances) {
        this.hideBalances = hideBalances;
    }
}
