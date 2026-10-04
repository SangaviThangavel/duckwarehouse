package com.duckwarehouse.persistence;

import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration
public class MongoConfiguration {
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        var options = TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
                .writeConcern(WriteConcern.MAJORITY).build();
        return new MongoTransactionManager(factory, options) {
            @Override
            protected void doCommit(MongoTransactionObject transaction) throws Exception {
                for (int attempt = 0; ; attempt++) {
                    try {
                        transaction.commitTransaction();
                        return;
                    } catch (MongoException error) {
                        // An ambiguous commit must retry COMMIT, never repeat the stock mutation.
                        if (!error.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
                                || attempt == 2) throw error;
                        Transactions.backoff(attempt);
                    }
                }
            }
        };
    }
}
