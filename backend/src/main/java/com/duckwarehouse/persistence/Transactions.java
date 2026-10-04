package com.duckwarehouse.persistence;

import com.duckwarehouse.api.ApiException;
import com.mongodb.MongoException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class Transactions {
    private final TransactionTemplate template;

    public Transactions(MongoTransactionManager manager) {
        template = new TransactionTemplate(manager);
    }

    public <T> T execute(Supplier<T> operation) {
        for (int attempt = 0; ; attempt++) {
            try {
                return template.execute(status -> operation.get());
            } catch (RuntimeException error) {
                if (hasLabel(error, MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COMMIT_OUTCOME_UNKNOWN",
                            "The write outcome is unknown. Reload inventory before submitting again.");
                }
                boolean retryable = error instanceof DuplicateKeyException
                        || hasLabel(error, MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                if (!retryable) throw error;
                if (attempt == 2) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_CONTENTION",
                            "Inventory is busy. No changes were committed; try again.");
                }
                backoff(attempt);
            }
        }
    }

    static boolean hasLabel(Throwable error, String label) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo && mongo.hasErrorLabel(label)) return true;
        }
        return false;
    }

    static void backoff(int attempt) {
        try {
            long minimum = attempt == 0 ? 50 : 150;
            Thread.sleep(ThreadLocalRandom.current().nextLong(minimum, minimum * 3));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_INTERRUPTED", "The write was interrupted.");
        }
    }
}
