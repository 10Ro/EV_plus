package com.porros365.evmining.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MethodologyStepCodeTest {

    @Test
    void shouldMapAllSixStepsInSequentialOrder() {
        assertEquals(6, MethodologyStepCode.values().length);
        assertNull(MethodologyStepCode.fromStepNumber(1).getDependsOnStepNumber());
        assertEquals("data-ingestion-service", MethodologyStepCode.fromStepNumber(1).getTargetService());
        assertEquals("data-processing-service", MethodologyStepCode.fromStepNumber(2).getTargetService());
        assertEquals(1, MethodologyStepCode.fromStepNumber(2).getDependsOnStepNumber());
        assertEquals("data-mining-service", MethodologyStepCode.fromStepNumber(3).getTargetService());
        assertEquals(5, MethodologyStepCode.fromStepNumber(6).getDependsOnStepNumber());
    }

    @Test
    void shouldRejectInvalidStepNumber() {
        assertThrows(IllegalArgumentException.class, () -> MethodologyStepCode.fromStepNumber(0));
        assertThrows(IllegalArgumentException.class, () -> MethodologyStepCode.fromStepNumber(7));
    }
}
