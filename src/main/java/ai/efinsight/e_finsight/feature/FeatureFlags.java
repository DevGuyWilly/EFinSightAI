package ai.efinsight.e_finsight.feature;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "feature")
public class FeatureFlags {
    private Adk adk = new Adk();

    public static class Adk {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public Adk getAdk() {
        return adk;
    }

    public void setAdk(Adk adk) {
        this.adk = adk;
    }
}
