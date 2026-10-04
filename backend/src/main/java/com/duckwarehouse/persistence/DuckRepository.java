package com.duckwarehouse.persistence;

import com.duckwarehouse.domain.*;
import java.math.BigDecimal;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class DuckRepository {
    private final MongoTemplate mongo;

    public DuckRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public List<Duck> list() {
        return mongo.find(Query.query(active()).with(Sort.by("quantity", "_id")), Duck.class);
    }

    public Duck find(int id) { return mongo.findById(id, Duck.class); }

    public Duck matching(Color color, Size size, BigDecimal price) {
        return mongo.findOne(Query.query(active().and("color").is(color).and("size").is(size)
                .and("price").is(price)), Duck.class);
    }

    public Duck cheapestSufficient(Color color, Size size, int quantity) {
        return mongo.findOne(Query.query(active().and("color").is(color).and("size").is(size)
                .and("quantity").gte(quantity)).with(Sort.by("price", "_id")), Duck.class);
    }

    public boolean hasVariant(Color color, Size size) {
        return mongo.exists(Query.query(active().and("color").is(color).and("size").is(size)), Duck.class);
    }

    public int nextId() {
        Document counter = mongo.findAndModify(Query.query(Criteria.where("_id").is("duck")),
                new Update().inc("value", 1), FindAndModifyOptions.options().returnNew(true),
                Document.class, "counters");
        return Math.toIntExact(((Number) counter.get("value")).longValue());
    }

    public Duck insert(Duck duck) { return mongo.insert(duck); }

    public Duck update(Duck current, Update update) {
        return mongo.findAndModify(Query.query(active().and("_id").is(current.id())
                        .and("version").is(current.version())), update.inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), Duck.class);
    }

    private Criteria active() { return Criteria.where("deleted").is(false); }
}
