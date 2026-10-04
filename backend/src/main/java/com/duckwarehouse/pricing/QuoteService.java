package com.duckwarehouse.pricing;

import com.duckwarehouse.api.ApiException;
import com.duckwarehouse.api.Requests;
import com.duckwarehouse.persistence.DuckRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {
    public record Quote(int duckId, BigDecimal unitPrice, String currency,
                        PackagingFactory.PackageType packageType,
                        List<PackagingFactory.Protection> protectionTypes,
                        BigDecimal total, List<PricingEngine.Entry> breakdown) {}

    private final DuckRepository ducks;
    private final PackagingFactory packaging;
    private final PricingEngine pricing;

    public QuoteService(DuckRepository ducks, PackagingFactory packaging, PricingEngine pricing) {
        this.ducks = ducks;
        this.packaging = packaging;
        this.pricing = pricing;
    }

    public Quote quote(Requests.Order order) {
        var duck = ducks.cheapestSufficient(order.color(), order.size(), order.quantity());
        if (duck == null) {
            if (!ducks.hasVariant(order.color(), order.size())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "No active duck matches this color and size.");
            }
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "No single record has sufficient stock.");
        }
        var pack = packaging.create(order.size(), order.shippingMode());
        var calculation = pricing.calculate(order.quantity(), duck.price(), pack.type(),
                order.destinationCountry(), order.shippingMode());
        return new Quote(duck.id(), duck.price(), "USD", pack.type(), pack.protections(),
                calculation.total(), calculation.breakdown());
    }
}
