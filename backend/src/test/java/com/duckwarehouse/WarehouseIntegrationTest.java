package com.duckwarehouse;

import com.duckwarehouse.api.*;
import com.duckwarehouse.domain.*;
import com.duckwarehouse.persistence.*;
import com.duckwarehouse.pricing.QuoteService;
import com.duckwarehouse.warehouse.WarehouseService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WarehouseIntegrationTest {
    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0.28").withReplicaSet();
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
    }
    @Autowired WarehouseService warehouse;
    @Autowired DuckRepository ducks;
    @Autowired Transactions transactions;
    @Autowired MongoTemplate template;
    @Autowired QuoteService quotes;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clear() {
        template.remove(new Query(), Duck.class);
        template.updateFirst(Query.query(Criteria.where("_id").is("duck")), new Update().set("value", 0), "counters");
    }

    @Test
    void simultaneousAddsKeepOneIdAndExactStock() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<WarehouseService.Mutation>>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> { start.await(); return warehouse.add(add("10.00", 7)); }));
            }
            start.countDown();
            var ids = new ArrayList<Integer>();
            for (var result : results) ids.add(result.get(30, TimeUnit.SECONDS).duck().id());
            assertThat(ids).containsOnly(ids.getFirst());
            assertThat(warehouse.list()).singleElement().satisfies(duck -> {
                assertThat(duck.quantity()).isEqualTo(56);
                assertThat(duck.version()).isEqualTo(7);
            });
        }
    }

    @Test
    void editCollisionAddsSubmittedQuantityAndDeletesSource() {
        Duck source = warehouse.add(add("10.00", 30)).duck();
        Duck target = warehouse.add(add("12.00", 5)).duck();
        var result = warehouse.edit(source.id(), new Requests.EditDuck(new BigDecimal("12.00"), 9, source.version()));
        assertThat(result.outcome()).isEqualTo("merged");
        assertThat(result.duck().id()).isEqualTo(target.id());
        assertThat(result.duck().quantity()).isEqualTo(14);
        assertThat(ducks.find(source.id()).deleted()).isTrue();
        assertThat(warehouse.list()).hasSize(1);
    }

    @Test
    void aFailureAfterBothWritesRollsBackEntireMerge() {
        Duck source = warehouse.add(add("10.00", 30)).duck();
        Duck target = warehouse.add(add("12.00", 5)).duck();
        assertThatThrownBy(() -> transactions.execute(() -> {
            ducks.update(target, new Update().set("quantity", 35));
            ducks.update(source, new Update().set("deleted", true));
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(ducks.find(source.id())).isEqualTo(source);
        assertThat(ducks.find(target.id())).isEqualTo(target);
    }

    @Test
    void staleEditCannotOverwriteAddedStock() {
        Duck original = warehouse.add(add("10.00", 10)).duck();
        warehouse.add(add("10.00", 5));
        assertThatThrownBy(() -> warehouse.edit(original.id(), new Requests.EditDuck(new BigDecimal("11.00"), 2, original.version())))
                .isInstanceOf(ApiException.class).hasMessageContaining("Inventory changed");
        assertThat(ducks.find(original.id()).quantity()).isEqualTo(15);
        assertThat(ducks.find(original.id()).price()).isEqualByComparingTo("10.00");
    }

    @Test
    void simultaneousEditsAllowOneVersionWinner() throws Exception {
        Duck duck = warehouse.add(add("10.00", 10)).duck();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var outcomes = new ArrayList<Future<String>>();
            for (int quantity : new int[]{20, 30}) outcomes.add(pool.submit(() -> {
                start.await();
                try {
                    warehouse.edit(duck.id(), new Requests.EditDuck(duck.price(), quantity, duck.version()));
                    return "updated";
                } catch (ApiException error) { return error.code(); }
            }));
            start.countDown();
            assertThat(outcomes.get(0).get()).isIn("updated", "STALE_VERSION");
            assertThat(java.util.List.of(outcomes.get(0).get(), outcomes.get(1).get()))
                    .containsExactlyInAnyOrder("updated", "STALE_VERSION");
            assertThat(ducks.find(duck.id()).quantity()).isIn(20, 30);
            assertThat(ducks.find(duck.id()).version()).isEqualTo(1);
        }
    }

    @Test
    void concurrentDeleteAndAddCannotResurrectOldRecord() throws Exception {
        Duck duck = warehouse.add(add("10.00", 10)).duck();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var deleting = pool.submit(() -> { start.await(); warehouse.delete(duck.id()); return null; });
            var adding = pool.submit(() -> { start.await(); return warehouse.add(add("10.00", 5)); });
            start.countDown();
            deleting.get(30, TimeUnit.SECONDS);
            var result = adding.get(30, TimeUnit.SECONDS);
            assertThat(ducks.find(duck.id()).deleted()).isTrue();
            if (result.duck().id().equals(duck.id())) assertThat(warehouse.list()).isEmpty();
            else assertThat(warehouse.list()).singleElement().satisfies(active -> assertThat(active.quantity()).isEqualTo(5));
        }
    }

    @Test
    void deletionIsIdempotentAndReaddingCreatesNewId() {
        Duck duck = warehouse.add(add("10.00", 10)).duck();
        warehouse.delete(duck.id()); warehouse.delete(duck.id());
        assertThat(warehouse.list()).isEmpty();
        Duck replacement = warehouse.add(add("10.00", 3)).duck();
        assertThat(replacement.id()).isNotEqualTo(duck.id());
        assertThat(replacement.quantity()).isEqualTo(3);
        assertThat(ducks.find(duck.id()).deleted()).isTrue();
    }

    @Test
    void stockOverflowRollsBackMerge() {
        Duck source = warehouse.add(add("10.00", 3)).duck();
        Duck target = warehouse.add(add("12.00", Integer.MAX_VALUE)).duck();
        assertThatThrownBy(() -> warehouse.edit(source.id(), new Requests.EditDuck(target.price(), 1, source.version())))
                .isInstanceOf(ApiException.class);
        assertThat(ducks.find(source.id())).isEqualTo(source);
        assertThat(ducks.find(target.id())).isEqualTo(target);
    }

    @Test
    void quoteChoosesCheapestSufficientRecordWithoutChangingStock() {
        warehouse.add(add("5.00", 100));
        Duck eligible = warehouse.add(add("10.00", 150)).duck();
        warehouse.add(add("12.00", 200));
        Duck deleted = warehouse.add(add("1.00", 500)).duck();
        warehouse.delete(deleted.id());
        var before = warehouse.list();
        var quote = quotes.quote(new Requests.Order(Color.Red, Size.Large, 101, "India", ShippingMode.Air));
        assertThat(quote.duckId()).isEqualTo(eligible.id());
        assertThat(quote.total()).isEqualByComparingTo("4080.40");
        assertThat(warehouse.list()).isEqualTo(before);
        assertThatThrownBy(() -> quotes.quote(new Requests.Order(Color.Red, Size.Large, 201, "USA", ShippingMode.Sea)))
                .isInstanceOf(ApiException.class).hasMessageContaining("single record");
    }

    @Test
    void listingSortsByQuantityThenId() {
        Duck second = warehouse.add(add("10.00", 20)).duck();
        Duck first = warehouse.add(add("11.00", 5)).duck();
        Duck third = warehouse.add(add("12.00", 20)).duck();
        assertThat(warehouse.list()).extracting(Duck::id).containsExactly(first.id(), second.id(), third.id());
    }

    @Test
    void apiValidatesFieldsAndRejectsImmutableAndUnknownValues() throws Exception {
        mvc.perform(post("/api/ducks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"Large\",\"price\":0,\"quantity\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.price").exists());
        mvc.perform(post("/api/ducks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Blue\",\"size\":\"Large\",\"price\":1,\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ducks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"Large\",\"price\":1.001,\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ducks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"Large\",\"price\":1,\"quantity\":1.5}"))
                .andExpect(status().isBadRequest());
        Duck duck = warehouse.add(add("10.00", 10)).duck();
        mvc.perform(put("/api/ducks/" + duck.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Green\",\"price\":11,\"quantity\":1,\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"Large\",\"quantity\":1,\"destinationCountry\":\"India\",\"shippingMode\":\"Air\",\"price\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiReportsNotFoundAndConflictAndReturnsReferenceQuote() throws Exception {
        mvc.perform(delete("/api/ducks/999")).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"Large\",\"quantity\":101,\"destinationCountry\":\"India\",\"shippingMode\":\"Air\"}"))
                .andExpect(status().isNotFound());
        Duck duck = warehouse.add(add("10.00", 150)).duck();
        mvc.perform(post("/api/orders/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"Large\",\"quantity\":151,\"destinationCountry\":\"India\",\"shippingMode\":\"Air\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"Large\",\"quantity\":101,\"destinationCountry\":\"India\",\"shippingMode\":\"Air\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4080.40))
                .andExpect(jsonPath("$.packageType").value("Wood"));
        warehouse.add(add("10.00", 1));
        mvc.perform(put("/api/ducks/" + duck.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":11,\"quantity\":1,\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    private Requests.AddDuck add(String price, int quantity) {
        return new Requests.AddDuck(Color.Red, Size.Large, new BigDecimal(price), quantity);
    }
}
