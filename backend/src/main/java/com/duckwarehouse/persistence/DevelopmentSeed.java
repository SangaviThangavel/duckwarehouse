package com.duckwarehouse.persistence;

import com.duckwarehouse.api.Requests;
import com.duckwarehouse.domain.*;
import com.duckwarehouse.warehouse.WarehouseService;
import java.math.BigDecimal;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("seed")
public class DevelopmentSeed implements ApplicationRunner {
    private final WarehouseService warehouse;
    private final DuckRepository ducks;

    public DevelopmentSeed(WarehouseService warehouse, DuckRepository ducks) {
        this.warehouse = warehouse;
        this.ducks = ducks;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ducks.list().isEmpty()) return;
        warehouse.add(new Requests.AddDuck(Color.Yellow, Size.Small, new BigDecimal("4.50"), 240));
        warehouse.add(new Requests.AddDuck(Color.Red, Size.Large, new BigDecimal("10.00"), 1500));
        warehouse.add(new Requests.AddDuck(Color.Green, Size.Medium, new BigDecimal("7.25"), 85));
        warehouse.add(new Requests.AddDuck(Color.Black, Size.XLarge, new BigDecimal("18.00"), 32));
    }
}
