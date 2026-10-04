package com.duckwarehouse.api;

import com.duckwarehouse.domain.Color;
import com.duckwarehouse.domain.Size;
import com.duckwarehouse.domain.ShippingMode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class Requests {
    private Requests() {}

    public record AddDuck(@NotNull Color color, @NotNull Size size,
                          @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal price,
                          @NotNull @Min(0) Integer quantity) {}

    public record EditDuck(@NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal price,
                           @NotNull @Min(0) Integer quantity, @NotNull @Min(0) Long version) {}

    public record Order(@NotNull Color color, @NotNull Size size,
                        @NotNull @Min(1) Integer quantity,
                        @NotBlank @jakarta.validation.constraints.Size(max = 100) String destinationCountry,
                        @NotNull ShippingMode shippingMode) {}
}
