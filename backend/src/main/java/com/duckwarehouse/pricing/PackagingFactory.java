package com.duckwarehouse.pricing;

import com.duckwarehouse.domain.ShippingMode;
import com.duckwarehouse.domain.Size;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PackagingFactory {
    public enum PackageType { Wood, Cardboard, Plastic }
    public enum Protection { PolystyreneBalls, BubbleWrapBags, MoistureAbsorbingBeads }
    public record Packaging(PackageType type, List<Protection> protections) {}

    public Packaging create(Size size, ShippingMode mode) {
        PackageType type = switch (size) {
            case XLarge, Large -> PackageType.Wood;
            case Medium -> PackageType.Cardboard;
            case Small, XSmall -> PackageType.Plastic;
        };
        List<Protection> protections = switch (mode) {
            case Land -> List.of(Protection.PolystyreneBalls);
            case Sea -> List.of(Protection.MoistureAbsorbingBeads, Protection.BubbleWrapBags);
            case Air -> List.of(type == PackageType.Plastic
                    ? Protection.BubbleWrapBags : Protection.PolystyreneBalls);
        };
        return new Packaging(type, protections);
    }
}
