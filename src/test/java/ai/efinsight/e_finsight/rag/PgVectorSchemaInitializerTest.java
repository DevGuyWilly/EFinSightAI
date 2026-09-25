package ai.efinsight.e_finsight.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

// Needs a real PostgreSQL with pgvector available; see PgVectorSearchTest for setup. Uses its own schema so it
// doesn't interfere with other tests.
@EnabledIfEnvironmentVariable(named = "TEST_PGVECTOR_URL", matches = ".+")
class PgVectorSchemaInitializerTest {
    private static final String SCHEMA = "pgvector_migration_test";

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createLegacyTextColumnTable() {
        String url = System.getenv("TEST_PGVECTOR_URL");
        DriverManagerDataSource admin = dataSource(url);
        new JdbcTemplate(admin).execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + SCHEMA);
        // Keep the extension in public; otherwise it would install into the test schema and be dropped with it
        new JdbcTemplate(admin).execute("CREATE EXTENSION IF NOT EXISTS vector SCHEMA public");

        dataSource = dataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
        jdbc = new JdbcTemplate(dataSource);
        // The schema as it existed before pgvector: embeddings stored as JSON-array text
        jdbc.execute("CREATE TABLE transaction_chunks (id BIGSERIAL PRIMARY KEY, user_id BIGINT, embedding TEXT)");
        jdbc.update("INSERT INTO transaction_chunks (embedding) VALUES ('[0.25,-0.5,1.0E-5]'), (NULL)");
    }

    @Test
    void convertsLegacyTextColumnToVectorWithoutLosingData() {
        new PgVectorSchemaInitializer(dataSource);

        assertThat(columnType()).isEqualTo("vector");
        assertThat(jdbc.queryForObject(
                "SELECT embedding::text FROM transaction_chunks WHERE embedding IS NOT NULL", String.class))
                .isEqualTo("[0.25,-0.5,1e-05]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_chunks", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = current_schema() AND indexname = 'idx_transaction_chunks_user_id'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void isSafeToRunOnEveryStartup() {
        new PgVectorSchemaInitializer(dataSource);
        new PgVectorSchemaInitializer(dataSource);

        assertThat(columnType()).isEqualTo("vector");
    }

    private String columnType() {
        return jdbc.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'transaction_chunks'
                  AND column_name = 'embedding'
                """, String.class);
    }

    private static DriverManagerDataSource dataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("TEST_PGVECTOR_USERNAME", "postgres"),
                System.getenv().getOrDefault("TEST_PGVECTOR_PASSWORD", ""));
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
