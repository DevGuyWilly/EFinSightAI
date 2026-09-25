package ai.efinsight.e_finsight.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Prepares PostgreSQL for pgvector similarity search before Hibernate touches the schema:
 * enables the {@code vector} extension (needed before Hibernate can create a {@code vector} column on a fresh
 * database) and converts a legacy TEXT {@code transaction_chunks.embedding} column in place. The TEXT column already
 * holds pgvector's text format ("[0.1,0.2,...]"), so the conversion is a lossless cast.
 *
 * Skipped for non-PostgreSQL databases (e.g. H2 in tests). Fails startup with a clear message if pgvector can't be
 * enabled, since RAG retrieval depends on it.
 */
@Component
public class PgVectorSchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(PgVectorSchemaInitializer.class);

    public PgVectorSchemaInitializer(DataSource dataSource) {
        if (!isPostgres(dataSource)) {
            log.info("Skipping pgvector setup: database is not PostgreSQL");
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");

            List<String> columnTypes = jdbc.queryForList("""
                    SELECT udt_name FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = 'transaction_chunks'
                      AND column_name = 'embedding'
                    """, String.class);
            if (!columnTypes.isEmpty()) {
                if (!"vector".equals(columnTypes.get(0))) {
                    log.info("Converting transaction_chunks.embedding from {} to vector", columnTypes.get(0));
                    jdbc.execute("ALTER TABLE transaction_chunks ALTER COLUMN embedding TYPE vector USING embedding::vector");
                }
                // Similarity search filters by user; fresh databases get this from TransactionChunk's @Index
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_transaction_chunks_user_id ON transaction_chunks (user_id)");
            }
            log.info("pgvector ready for transaction_chunks.embedding");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not set up pgvector for RAG retrieval. The database user needs permission to run "
                            + "'CREATE EXTENSION vector' (enabled by default on Supabase; for a local Postgres, install "
                            + "pgvector, e.g. 'brew install pgvector'). Cause: " + e.getMessage(), e);
        }
    }

    private static boolean isPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not connect to the database to check for pgvector support", e);
        }
    }

    // Makes Hibernate's EntityManagerFactory (and so its ddl-auto schema update) wait for this initializer
    @Component
    static class EntityManagerFactoryDependsOnPgVector extends EntityManagerFactoryDependsOnPostProcessor {
        EntityManagerFactoryDependsOnPgVector() {
            super(PgVectorSchemaInitializer.class);
        }
    }
}
