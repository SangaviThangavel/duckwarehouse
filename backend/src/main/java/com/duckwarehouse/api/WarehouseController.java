package com.duckwarehouse.api;

import com.duckwarehouse.domain.Duck;
import com.duckwarehouse.warehouse.WarehouseService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ducks")
public class WarehouseController {
    private final WarehouseService warehouse;

    public WarehouseController(WarehouseService warehouse) { this.warehouse = warehouse; }

    @GetMapping
    public List<Duck> list() { return warehouse.list(); }

    @PostMapping
    public ResponseEntity<WarehouseService.Mutation> add(@Valid @RequestBody Requests.AddDuck request) {
        var result = warehouse.add(request);
        return result.outcome().equals("created")
                ? ResponseEntity.created(URI.create("/api/ducks/" + result.duck().id())).body(result)
                : ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public WarehouseService.Mutation edit(@PathVariable int id, @Valid @RequestBody Requests.EditDuck request) {
        return warehouse.edit(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        warehouse.delete(id);
        return ResponseEntity.noContent().build();
    }
}
