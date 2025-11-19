package ai.efinsight.e_finsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vertex.ai")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "vertex.ai.project-id",
    matchIfMissing = false
)
public class VertexAIConfig {
    private String projectId;
    private String location = "us-central1";
    private String indexId;
    private String indexEndpointId;
    private String indexDeploymentId;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getIndexId() {
        return indexId;
    }

    public void setIndexId(String indexId) {
        this.indexId = indexId;
    }

    public String getIndexEndpointId() {
        return indexEndpointId;
    }

    public void setIndexEndpointId(String indexEndpointId) {
        this.indexEndpointId = indexEndpointId;
    }

    public String getIndexDeploymentId() {
        return indexDeploymentId;
    }

    public void setIndexDeploymentId(String indexDeploymentId) {
        this.indexDeploymentId = indexDeploymentId;
    }

    public String getIndexEndpoint() {
        return String.format("projects/%s/locations/%s/indexEndpoints/%s", projectId, location, indexEndpointId);
    }

    public String getIndexName() {
        return String.format("projects/%s/locations/%s/indexes/%s", projectId, location, indexId);
    }
}

