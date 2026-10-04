package com.duckwarehouse.persistence;

import com.duckwarehouse.domain.Duck;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSetup implements InitializingBean {
    private final MongoTemplate mongo;

    public DatabaseSetup(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void afterPropertiesSet() {
        // Collections and indexes must exist before serving transactional requests.
        if (!mongo.collectionExists("ducks")) mongo.createCollection("ducks");
        if (!mongo.collectionExists("counters")) mongo.createCollection("counters");
        mongo.upsert(Query.query(Criteria.where("_id").is("duck")),
                new Update().setOnInsert("value", 0), "counters");
        mongo.indexOps(Duck.class).ensureIndex(new Index().on("color", Sort.Direction.ASC)
                .on("size", Sort.Direction.ASC).on("price", Sort.Direction.ASC)
                .unique().named("active_duck_key")
                .partial(PartialIndexFilter.of(new Document("deleted", false))));
        mongo.indexOps(Duck.class).ensureIndex(new Index().on("deleted", Sort.Direction.ASC)
                .on("quantity", Sort.Direction.ASC).on("_id", Sort.Direction.ASC).named("warehouse_listing"));
    }
}
