package com.duckwarehouse.warehouse;

import com.duckwarehouse.api.ApiException;
import com.duckwarehouse.api.Requests;
import com.duckwarehouse.domain.Duck;
import com.duckwarehouse.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WarehouseService {
    public record Mutation(Duck duck, String outcome) {}
    private final DuckRepository ducks;
    private final Transactions transactions;

    public WarehouseService(DuckRepository ducks, Transactions transactions) {
        this.ducks = ducks;
        this.transactions = transactions;
    }

    public List<Duck> list() { return ducks.list(); }

    public Mutation add(Requests.AddDuck request) {
        return transactions.execute(() -> {
            BigDecimal price = request.price().setScale(2);
            Duck existing = ducks.matching(request.color(), request.size(), price);
            if (existing != null) {
                int total = stockSum(existing.quantity(), request.quantity());
                return new Mutation(updated(existing, new Update().set("quantity", total)), "merged");
            }
            int id;
            try { id = ducks.nextId(); }
            catch (ArithmeticException error) { throw invalid("The identifier limit has been reached."); }
            return new Mutation(ducks.insert(new Duck(id, request.color(), request.size(), price,
                    request.quantity(), false, 0)), "created");
        });
    }

    public Mutation edit(int id, Requests.EditDuck request) {
        return transactions.execute(() -> {
            Duck source = active(id);
            if (source.version() != request.version()) throw stale();
            BigDecimal price = request.price().setScale(2);
            Duck target = ducks.matching(source.color(), source.size(), price);
            if (target != null && !target.id().equals(source.id())) {
                int total = stockSum(target.quantity(), request.quantity());
                Duck survivor = updated(target, new Update().set("quantity", total));
                updated(source, new Update().set("deleted", true));
                return new Mutation(survivor, "merged");
            }
            return new Mutation(updated(source, new Update().set("price", price)
                    .set("quantity", request.quantity())), "updated");
        });
    }

    public void delete(int id) {
        transactions.execute(() -> {
            Duck duck = ducks.find(id);
            if (duck == null) throw missing();
            if (!duck.deleted()) updated(duck, new Update().set("deleted", true));
            return null;
        });
    }

    private Duck active(int id) {
        Duck duck = ducks.find(id);
        if (duck == null || duck.deleted()) throw missing();
        return duck;
    }

    private Duck updated(Duck duck, Update update) {
        Duck result = ducks.update(duck, update);
        if (result == null) throw stale();
        return result;
    }

    private int stockSum(int current, int added) {
        try { return Math.addExact(current, added); }
        catch (ArithmeticException error) { throw invalid("The combined stock exceeds the integer limit."); }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_LIMIT", message);
    }
    private ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "DUCK_NOT_FOUND", "The active duck was not found.");
    }
    private ApiException stale() {
        return new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", "Inventory changed. Reload the duck before editing.");
    }
}
