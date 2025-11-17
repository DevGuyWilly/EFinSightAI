package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {
    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);
    private static final int MAX_CHUNK_SIZE = 500;

    public List<String> chunkTransaction(Transaction transaction) {
        List<String> chunks = new ArrayList<>();
        String text = transaction.toTextSummary();
        
        if (text.length() > MAX_CHUNK_SIZE) {
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
                chunks.add(text.substring(start, end));
                start = end;
            }
        } else {
            chunks.add(text);
        }
        
        return chunks;
    }

    public List<String> chunkTransactions(List<Transaction> transactions) {
        List<String> allChunks = new ArrayList<>();
        for (Transaction transaction : transactions) {
            allChunks.addAll(chunkTransaction(transaction));
        }
        log.info("Created {} chunks from {} transactions", allChunks.size(), transactions.size());
        return allChunks;
    }
}

