package com.duckwarehouse.persistence;

import com.duckwarehouse.api.ApiException;
import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.session.ServerSession;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommitRetryTest {
    @Test
    void uncertainCommitRetriesSameSessionWithoutRepeatingBusinessOperation() {
        ClientSession session = mock(ClientSession.class);
        when(session.getServerSession()).thenReturn(mock(ServerSession.class));
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        when(factory.getSession(any(ClientSessionOptions.class))).thenReturn(session);
        doThrow(unknown()).doNothing().when(session).commitTransaction();
        var calls = new AtomicInteger();
        var transactions = new Transactions(new MongoConfiguration().transactionManager(factory));

        assertThat(transactions.execute(calls::incrementAndGet)).isEqualTo(1);
        assertThat(calls).hasValue(1);
        verify(session, times(1)).startTransaction(any());
        verify(session, times(2)).commitTransaction();
        verify(session).close();
    }

    @Test
    void uncertainCommitExhaustionReturnsUnknownOutcomeWithoutRepeatingOperation() {
        ClientSession session = mock(ClientSession.class);
        when(session.getServerSession()).thenReturn(mock(ServerSession.class));
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        when(factory.getSession(any(ClientSessionOptions.class))).thenReturn(session);
        doThrow(unknown()).when(session).commitTransaction();
        var calls = new AtomicInteger();
        var transactions = new Transactions(new MongoConfiguration().transactionManager(factory));

        assertThatThrownBy(() -> transactions.execute(calls::incrementAndGet))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("COMMIT_OUTCOME_UNKNOWN"));
        assertThat(calls).hasValue(1);
        verify(session, times(3)).commitTransaction();
        verify(session, never()).abortTransaction();
    }

    private MongoException unknown() {
        var error = new MongoException("simulated uncertain commit");
        error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        return error;
    }
}
