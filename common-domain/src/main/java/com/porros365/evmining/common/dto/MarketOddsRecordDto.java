package com.porros365.evmining.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MarketOddsRecordDto(
        @NotBlank String eventId,
        String sport,
        String competition,
        @NotBlank String eventName,
        @NotBlank String marketType,
        String marketLine,
        @NotBlank String selectionName,
        @NotBlank String sharpBookmaker,
        @NotNull @DecimalMin("1.001") Double sharpOddsSelection,
        @NotNull @DecimalMin("1.001") Double sharpOddsOpposite,
        Double sharpOddsDraw,
        @NotBlank String softBookmaker,
        @NotNull @DecimalMin("1.001") Double softTakenOdds,
        Double closingFairOdds,
        String outcomeStatus,
        // Enriched fields populated across steps 3 to 6
        Double impliedProbabilityRaw,
        Double sharpMarginPct,
        Double trueProbability,
        Double fairOdds,
        Double expectedValue,
        Double expectedValuePct,
        Boolean evPlusOpportunity,
        Double assignedStakeUnits,
        Double expectedProfitUnits,
        Double clvValue,
        Double clvPct,
        Boolean beatClosingLine,
        Double actualProfitUnits
) {
}
