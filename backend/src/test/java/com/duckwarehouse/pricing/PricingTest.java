package com.duckwarehouse.pricing;

import com.duckwarehouse.domain.ShippingMode;
import com.duckwarehouse.pricing.PackagingFactory.PackageType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class PricingTest {
    private final PricingEngine pricing = new PricingEngine();

    @Test
    void referenceQuoteUsesOriginalBaseForEveryPercentage() {
        var quote = pricing.calculate(101, new BigDecimal("10.00"), PackageType.Wood, "India", ShippingMode.Air);
        assertThat(quote.total()).isEqualByComparingTo("4080.40");
        assertThat(quote.breakdown()).extracting(PricingEngine.Entry::amount)
                .containsExactly(new BigDecimal("1010.00"), new BigDecimal("-202.00"),
                        new BigDecimal("50.50"), new BigDecimal("191.90"), new BigDecimal("3030.00"));
        assertReconciles(quote);
    }

    @ParameterizedTest
    @CsvSource({"100,1000.00,0", "101,1010.00,-202.00"})
    void bulkThresholdIsStrict(int quantity, String base, String discount) {
        var quote = pricing.calculate(quantity, new BigDecimal("10.00"), PackageType.Wood, "India", ShippingMode.Sea);
        BigDecimal applied = quote.breakdown().stream().filter(entry -> entry.code().equals("BULK_DISCOUNT"))
                .map(PricingEngine.Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(quote.breakdown().getFirst().amount()).isEqualByComparingTo(base);
        assertThat(applied).isEqualByComparingTo(discount);
        assertReconciles(quote);
    }

    @ParameterizedTest
    @CsvSource({"1000,31050.00,0", "1001,26576.55,-4504.50"})
    void airThresholdAndDiscountUseShippingCharge(int quantity, String expectedTotal, String expectedDiscount) {
        var quote = pricing.calculate(quantity, new BigDecimal("1.00"), PackageType.Plastic, "Canada", ShippingMode.Air);
        assertThat(quote.total()).isEqualByComparingTo(expectedTotal);
        BigDecimal discount = quote.breakdown().stream().filter(entry -> entry.code().equals("AIR_DISCOUNT"))
                .map(PricingEngine.Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(discount).isEqualByComparingTo(expectedDiscount);
        assertReconciles(quote);
    }

    @ParameterizedTest
    @CsvSource({"USA,18.00", "us,18.00", "United States,18.00", "Bolivia,13.00", "BO,13.00",
            "India,19.00", "in,19.00", "Canada,15.00"})
    void destinationRatesAndAliases(String destination, String expectedAmount) {
        var quote = pricing.calculate(1, new BigDecimal("100.00"), PackageType.Wood, "  " + destination + "  ", ShippingMode.Sea);
        assertThat(quote.breakdown().stream().filter(entry -> entry.code().equals("DESTINATION_ADJUSTMENT"))
                .findFirst().orElseThrow().amount()).isEqualByComparingTo(expectedAmount);
        assertReconciles(quote);
    }

    @ParameterizedTest
    @CsvSource({"Wood,5.00", "Cardboard,-1.00", "Plastic,10.00"})
    void packageAdjustments(PackageType type, String expectedAmount) {
        var quote = pricing.calculate(1, new BigDecimal("100.00"), type, "Canada", ShippingMode.Sea);
        assertThat(quote.breakdown().stream().filter(entry -> entry.code().equals("PACKAGE_ADJUSTMENT"))
                .findFirst().orElseThrow().amount()).isEqualByComparingTo(expectedAmount);
        assertReconciles(quote);
    }

    @Test
    void positiveHalfCentRoundsUpBeforeSummation() {
        var quote = pricing.calculate(1, new BigDecimal("0.10"), PackageType.Wood, "India", ShippingMode.Sea);
        assertThat(quote.total()).isEqualByComparingTo("400.13");
        assertReconciles(quote);
    }

    @Test
    void negativeHalfCentRoundsAwayFromZero() {
        var quote = pricing.calculate(1, new BigDecimal("0.50"), PackageType.Cardboard, "Canada", ShippingMode.Land);
        assertThat(quote.total()).isEqualByComparingTo("10.57");
        assertThat(quote.breakdown().get(1).amount()).isEqualByComparingTo("-0.01");
        assertReconciles(quote);
    }

    private void assertReconciles(PricingEngine.Calculation quote) {
        assertThat(quote.breakdown().stream().map(PricingEngine.Entry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(quote.total());
        assertThat(quote.breakdown()).allSatisfy(entry -> assertThat(entry.amount().scale()).isEqualTo(2));
    }
}
