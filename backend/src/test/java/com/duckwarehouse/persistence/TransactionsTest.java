package com.duckwarehouse.persistence;

import com.duckwarehouse.api.ApiException;
import com.mongodb.MongoException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionsTest {
    @Test
    void transientFailuresRetryInNewTransactions() {
        var manager = manager();
        var transactions = new Transactions(manager);
        var attempts = new AtomicInteger();
        String result = transactions.execute(() -> {
            if (attempts.incrementAndGet() < 3) throw labelled(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
            return "committed";
        });
        assertThat(result).isEqualTo("committed");
        verify(manager, times(3)).getTransaction(any(TransactionDefinition.class));
        verify(manager, times(2)).rollback(any());
        verify(manager, times(1)).commit(any());
    }

    @Test
    void retryExhaustionReturnsContentionWithoutCommit() {
        var manager = manager();
        var transactions = new Transactions(manager);
        assertThatThrownBy(() -> transactions.execute(() -> { throw labelled(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL); }))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("WRITE_CONTENTION"));
        verify(manager, times(3)).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void anUnknownCommitNeverRepeatsTheMutation() {
        var manager = manager();
        doThrow(labelled(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)).when(manager).commit(any());
        var transactions = new Transactions(manager);
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> transactions.execute(calls::incrementAndGet))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("COMMIT_OUTCOME_UNKNOWN"));
        assertThat(calls).hasValue(1);
        verify(manager, times(1)).getTransaction(any(TransactionDefinition.class));
    }

    private MongoTransactionManager manager() {
        var manager = mock(MongoTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> new SimpleTransactionStatus());
        return manager;
    }

    private MongoException labelled(String label) {
        var error = new MongoException("simulated database conflict");
        error.addLabel(label);
        return error;
    }
}
