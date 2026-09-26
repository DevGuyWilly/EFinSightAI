package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.rag.EmbeddingService;
import ai.efinsight.e_finsight.rag.RagService;
import ai.efinsight.e_finsight.rag.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RagTestControllerToggleTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RagService.class, () -> mock(RagService.class))
            .withBean(VectorStoreService.class, () -> mock(VectorStoreService.class))
            .withBean(EmbeddingService.class, () -> mock(EmbeddingService.class))
            .withUserConfiguration(RagTestController.class);

    @Test
    void debugEndpointsAreOffByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RagTestController.class));
    }

    @Test
    void debugEndpointsCanBeTurnedOn() {
        runner.withPropertyValues("app.debug-endpoints.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RagTestController.class));
    }
}
