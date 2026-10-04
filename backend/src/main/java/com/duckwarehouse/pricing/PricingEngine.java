package com.duckwarehouse.pricing;

import com.duckwarehouse.domain.ShippingMode;
import com.duckwarehouse.pricing.PackagingFactory.PackageType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PricingEngine {
    public record Entry(String code, String label, BigDecimal basis, BigDecimal rate, BigDecimal amount) {}
    public record Calculation(BigDecimal total, List<Entry> breakdown) {}
    private record Context(int quantity, BigDecimal base, PackageType packaging,
                           String destination, ShippingMode mode) {}

    @FunctionalInterface
    private interface PricingRule { List<Entry> apply(Context context); }

    // Each strategy independently emits signed entries; none mutates a running subtotal.
    private final List<PricingRule> rules = List.of(
            this::bulkDiscount, this::packageAdjustment, this::destinationAdjustment, this::shipping);

    public Calculation calculate(int quantity, BigDecimal unitPrice, PackageType packaging,
                                 String destination, ShippingMode mode) {
        var context = new Context(quantity, unitPrice.multiply(BigDecimal.valueOf(quantity)),
                packaging, destination, mode);
        var entries = new ArrayList<Entry>();
        entries.add(new Entry("MERCHANDISE", "Merchandise", money(context.base), null, money(context.base)));
        rules.forEach(rule -> entries.addAll(rule.apply(context)));
        BigDecimal total = entries.stream().map(Entry::amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new Calculation(total, List.copyOf(entries));
    }

    private List<Entry> bulkDiscount(Context context) {
        return context.quantity > 100
                ? List.of(percentage("BULK_DISCOUNT", "Quantity discount", context.base, "-0.20")) : List.of();
    }

    private List<Entry> packageAdjustment(Context context) {
        String rate = switch (context.packaging) {
            case Wood -> "0.05";
            case Plastic -> "0.10";
            case Cardboard -> "-0.01";
        };
        return List.of(percentage("PACKAGE_ADJUSTMENT", context.packaging + " package", context.base, rate));
    }

    private List<Entry> destinationAdjustment(Context context) {
        String country = context.destination.strip().toUpperCase(Locale.ROOT);
        String rate = switch (country) {
            case "US", "USA", "UNITED STATES" -> "0.18";
            case "BO", "BOLIVIA" -> "0.13";
            case "IN", "INDIA" -> "0.19";
            default -> "0.15";
        };
        return List.of(percentage("DESTINATION_ADJUSTMENT", "Destination adjustment", context.base, rate));
    }

    private List<Entry> shipping(Context context) {
        BigDecimal quantity = BigDecimal.valueOf(context.quantity);
        BigDecimal charge = switch (context.mode) {
            case Sea -> new BigDecimal("400.00");
            case Land -> quantity.multiply(new BigDecimal("10.00"));
            case Air -> quantity.multiply(new BigDecimal("30.00"));
        };
        Entry shipping = new Entry("SHIPPING", context.mode + " shipping", money(charge), null, money(charge));
        return context.mode == ShippingMode.Air && context.quantity > 1000
                ? List.of(shipping, percentage("AIR_DISCOUNT", "Air shipping discount", charge, "-0.15"))
                : List.of(shipping);
    }

    private Entry percentage(String code, String label, BigDecimal basis, String value) {
        BigDecimal rate = new BigDecimal(value);
        return new Entry(code, label, money(basis), rate, money(basis.multiply(rate)));
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
