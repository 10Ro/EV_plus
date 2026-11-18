package com.porros365.evmining.common.domain;

public enum MethodologyStepCode {
    ODDS_INGESTION_AND_CONSOLIDATION(1, "Ingestión y Consolidación de Mercados Sharp y Soft", "data-ingestion-service", null),
    MARKET_CLEANING_AND_NORMALIZATION(2, "Limpieza, Estandarización y Detección de Anomalías de Cuota", "data-processing-service", 1),
    SHARP_DEVIGGING_AND_FAIR_ODDS(3, "Eliminación de Margen (De-Vigging) y Cálculo de Cuota Justa", "data-mining-service", 2),
    EV_PLUS_OPPORTUNITY_MINING(4, "Detección y Clasificación de Apuestas con Valor Esperado Positivo (EV+)", "data-mining-service", 3),
    STAKE_AND_VOLUME_SIMULATION(5, "Dimensionamiento de Stake, Expectativa a Largo Plazo y Simulación Monte Carlo", "data-mining-service", 4),
    CLV_AND_PERFORMANCE_AUDIT(6, "Auditoría de CLV (Closing Line Value) y Evaluación Estadística de Ventaja", "data-mining-service", 5);

    private final int stepNumber;
    private final String displayName;
    private final String targetService;
    private final Integer dependsOnStepNumber;

    MethodologyStepCode(int stepNumber, String displayName, String targetService, Integer dependsOnStepNumber) {
        this.stepNumber = stepNumber;
        this.displayName = displayName;
        this.targetService = targetService;
        this.dependsOnStepNumber = dependsOnStepNumber;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTargetService() {
        return targetService;
    }

    public Integer getDependsOnStepNumber() {
        return dependsOnStepNumber;
    }

    public static MethodologyStepCode fromStepNumber(int stepNumber) {
        for (MethodologyStepCode value : values()) {
            if (value.stepNumber == stepNumber) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid methodology step number: " + stepNumber);
    }
}
