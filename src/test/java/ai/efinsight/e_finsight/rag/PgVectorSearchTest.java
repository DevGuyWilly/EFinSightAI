package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.security.TestTokenCipherConfig;
import ai.efinsight.e_finsight.llm.LLMConfig;
import ai.efinsight.e_finsight.model.TransactionChunk;
import ai.efinsight.e_finsight.repository.TransactionChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Needs a real PostgreSQL with the pgvector extension available, e.g.
//   docker run -d -p 5433:5432 -e POSTGRES_PASSWORD=test pgvector/pgvector:pg17
//   TEST_PGVECTOR_URL=jdbc:postgresql://localhost:5433/postgres TEST_PGVECTOR_USERNAME=postgres TEST_PGVECTOR_PASSWORD=test
@EnabledIfEnvironmentVariable(named = "TEST_PGVECTOR_URL", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_PGVECTOR_URL}",
        "spring.datasource.username=${TEST_PGVECTOR_USERNAME:postgres}",
        "spring.datasource.password=${TEST_PGVECTOR_PASSWORD:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "llm.provider=gemini"
})
@Import({PgVectorSchemaInitializer.class, PgVectorSchemaInitializer.EntityManagerFactoryDependsOnPgVector.class,
        VectorStoreService.class, EmbeddingService.class, LLMConfig.class, TestTokenCipherConfig.class})
class PgVectorSearchTest {
    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private TransactionChunkRepository chunkRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seed() {
        chunkRepository.deleteAll();
        store(USER, 1L, "exact match", 1f, 0f, 0f);
        store(USER, 2L, "close match", 0.8f, 0.6f, 0f);
        store(USER, 3L, "orthogonal", 0f, 1f, 0f);
        store(USER, 4L, "zero vector", 0f, 0f, 0f);
        store(USER, 5L, "different model dimensions", 1f, 0f);
        store(OTHER_USER, 6L, "other user's identical vector", 1f, 0f, 0f);
    }

    @Test
    void returnsTheUsersChunksNearestFirstWithCosineSimilarity() {
        List<VectorStoreService.ChunkSimilarity> results =
                vectorStoreService.searchSimilarWithScores(USER, new float[]{1f, 0f, 0f}, 10);

        assertThat(results).extracting(r -> r.chunk.getChunkText())
                .containsExactly("exact match", "close match", "orthogonal");
        assertThat(results.get(0).similarity).isCloseTo(1.0, within(1e-6));
        assertThat(results.get(1).similarity).isCloseTo(0.8, within(1e-6));
        assertThat(results.get(2).similarity).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void freshSchemaGetsTheUserIdIndex() {
        Integer indexes = (Integer) entityManager.getEntityManager().createNativeQuery(
                "SELECT count(*)::int FROM pg_indexes WHERE schemaname = current_schema() AND indexname = 'idx_transaction_chunks_user_id'")
                .getSingleResult();

        assertThat(indexes).isEqualTo(1);
    }

    @Test
    void respectsTopK() {
        assertThat(vectorStoreService.searchSimilarWithScores(USER, new float[]{1f, 0f, 0f}, 2))
                .extracting(r -> r.chunk.getChunkText())
                .containsExactly("exact match", "close match");
    }

    @Test
    void embeddingRoundTripsThroughTheVectorColumn() {
        // Force a real read from the database rather than the entity cached from the save
        entityManager.flush();
        entityManager.clear();

        TransactionChunk chunk = chunkRepository.findByTransactionId(2L).get(0);

        assertThat(chunk.getEmbedding()).isEqualTo("[0.8,0.6,0]");
    }

    private void store(long userId, long transactionId, String text, float... embedding) {
        vectorStoreService.storeChunk(userId, transactionId, text, embedding, 0);
    }
}
