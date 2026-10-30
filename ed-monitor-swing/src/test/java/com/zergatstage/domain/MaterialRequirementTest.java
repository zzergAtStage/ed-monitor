package com.zergatstage.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Guards the behavior of MaterialRequirement's delivered-quantity accumulation and remaining-quantity calculation.
 */
class MaterialRequirementTest {

    @Test
    void remainingQuantityEqualsRequiredMinusDeliveredWhenPartiallyDelivered() {
        MaterialRequirement requirement = MaterialRequirement.builder()
                .requiredQuantity(100)
                .deliveredQuantity(30)
                .build();

        assertEquals(70, requirement.getRemainingQuantity());
    }

    @Test
    void remainingQuantityIsZeroWhenFullyDelivered() {
        MaterialRequirement requirement = MaterialRequirement.builder()
                .requiredQuantity(50)
                .deliveredQuantity(50)
                .build();

        assertEquals(0, requirement.getRemainingQuantity());
    }

    @Test
    void remainingQuantityIsClampedToZeroWhenOverDelivered() {
        MaterialRequirement requirement = MaterialRequirement.builder()
                .requiredQuantity(40)
                .deliveredQuantity(60)
                .build();

        assertEquals(0, requirement.getRemainingQuantity());
    }

    @Test
    void addDeliveredQuantityAccumulatesToExistingAmount() {
        MaterialRequirement requirement = MaterialRequirement.builder()
                .requiredQuantity(100)
                .deliveredQuantity(20)
                .build();

        requirement.addDeliveredQuantity(15);
        requirement.addDeliveredQuantity(10);

        assertEquals(45, requirement.getDeliveredQuantity());
    }

    @Test
    void addDeliveredQuantityReducesRemainingQuantity() {
        MaterialRequirement requirement = MaterialRequirement.builder()
                .requiredQuantity(100)
                .deliveredQuantity(30)
                .build();

        requirement.addDeliveredQuantity(20);

        assertEquals(50, requirement.getRemainingQuantity());
    }

    @Test
    void FreshlyBuiltRequirementHasZeroDeliveredAndRemainingEqualsRequired() {
        MaterialRequirement requirement = MaterialRequirement.builder()
                .requiredQuantity(80)
                .build();

        assertEquals(0, requirement.getDeliveredQuantity());
        assertEquals(80, requirement.getRemainingQuantity());
    }
}
