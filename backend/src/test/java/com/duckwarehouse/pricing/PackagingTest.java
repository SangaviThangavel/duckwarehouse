package com.duckwarehouse.pricing;

import com.duckwarehouse.domain.*;
import com.duckwarehouse.pricing.PackagingFactory.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.assertThat;

class PackagingTest {
    private final PackagingFactory factory = new PackagingFactory();

    static Stream<Arguments> combinations() {
        return Stream.of(Size.values()).flatMap(size -> Stream.of(ShippingMode.values())
                .map(mode -> Arguments.of(size, mode)));
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void packagesAllFifteenSizeAndModeCombinations(Size size, ShippingMode mode) {
        PackageType expectedPackage = switch (size) {
            case XLarge, Large -> PackageType.Wood;
            case Medium -> PackageType.Cardboard;
            case Small, XSmall -> PackageType.Plastic;
        };
        List<Protection> expectedProtections = mode == ShippingMode.Sea
                ? List.of(Protection.MoistureAbsorbingBeads, Protection.BubbleWrapBags)
                : List.of(mode == ShippingMode.Air && expectedPackage == PackageType.Plastic
                    ? Protection.BubbleWrapBags : Protection.PolystyreneBalls);
        assertThat(factory.create(size, mode)).isEqualTo(new Packaging(expectedPackage, expectedProtections));
    }
}
