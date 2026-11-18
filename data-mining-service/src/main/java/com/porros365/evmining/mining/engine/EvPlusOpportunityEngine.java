package com.porros365.evmining.mining.engine;

import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paso 4 de la Metodologia (@Porros365 - Posts 2/, 3/, 6/, 10/, 11/):
 * Calculo de Valor Esperado: EV = (probabilidad_real * cuota_soft) - 1
 * Filtrado y clasificacion de oportunidades EV+ en casas blandas (Bet365, Paf, etc.).
 */
@Component
public class EvPlusOpportunityEngine {

    public EvMiningBatchResult mineEvPlusOpportunities(
            List<MarketOddsRecordDto> inputRecords,
            double minEvThreshold,
            double maxEvThreshold) {

        List<MarketOddsRecordDto> enriched = new ArrayList<>(inputRecords.size());
        DescriptiveStatistics allEvPctStats = new DescriptiveStatistics();
        DescriptiveStatistics evPlusOnlyStats = new DescriptiveStatistics();

        int evPlusCount = 0;
        int palpableAlertCount = 0;

        for (MarketOddsRecordDto r : inputRecords) {
            // If running in independentMode without prior Step 3 execution, derive trueProbability on the fly
            double trueProb;
            double fairOdds;
            if (r.trueProbability() != null && r.trueProbability() > 0.0) {
                trueProb = r.trueProbability();
                fairOdds = r.fairOdds() != null ? r.fairOdds() : (1.0 / trueProb);
            } else {
                double sumImplied = (1.0 / r.sharpOddsSelection()) + (1.0 / r.sharpOddsOpposite())
                        + (("MATCH_ODDS_1X2".equalsIgnoreCase(r.marketType()) && r.sharpOddsDraw() != null)
                        ? (1.0 / r.sharpOddsDraw()) : 0.0);
                trueProb = (1.0 / r.sharpOddsSelection()) / sumImplied;
                fairOdds = 1.0 / trueProb;
            }

            // Core formula from Post 3/: EV = (probabilidad_real * cuota) - 1
            double ev = (trueProb * r.softTakenOdds()) - 1.0;
            double evPct = ev * 100.0;
            boolean isEvPlus = ev >= minEvThreshold && ev <= maxEvThreshold;

            if (ev > maxEvThreshold) {
                palpableAlertCount++;
            }
            if (isEvPlus) {
                evPlusCount++;
                evPlusOnlyStats.addValue(evPct);
            }
            allEvPctStats.addValue(evPct);

            enriched.add(new MarketOddsRecordDto(
                    r.eventId(),
                    r.sport(),
                    r.competition(),
                    r.eventName(),
                    r.marketType(),
                    r.marketLine(),
                    r.selectionName(),
                    r.sharpBookmaker(),
                    r.sharpOddsSelection(),
                    r.sharpOddsOpposite(),
                    r.sharpOddsDraw(),
                    r.softBookmaker(),
                    r.softTakenOdds(),
                    r.closingFairOdds(),
                    r.outcomeStatus(),
                    r.impliedProbabilityRaw() != null ? r.impliedProbabilityRaw() : round6(1.0 / r.sharpOddsSelection()),
                    r.sharpMarginPct(),
                    round6(trueProb),
                    round4(fairOdds),
                    round6(ev),
                    round4(evPct),
                    isEvPlus,
                    r.assignedStakeUnits(),
                    r.expectedProfitUnits(),
                    r.clvValue(),
                    r.clvPct(),
                    r.beatClosingLine(),
                    r.actualProfitUnits()
            ));
        }

        List<Map<String, Object>> topOpportunities = enriched.stream()
                .filter(rec -> Boolean.TRUE.equals(rec.evPlusOpportunity()))
                .sorted(Comparator.comparing(MarketOddsRecordDto::expectedValuePct).reversed())
                .map(rec -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("eventId", rec.eventId());
                    item.put("eventName", rec.eventName());
                    item.put("selectionName", rec.selectionName());
                    item.put("softBookmaker", rec.softBookmaker());
                    item.put("softTakenOdds", rec.softTakenOdds());
                    item.put("fairOdds", rec.fairOdds());
                    item.put("trueProbabilityPct", round4(rec.trueProbability() * 100.0));
                    item.put("expectedValuePct", rec.expectedValuePct());
                    return item;
                })
                .toList();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("stepNumber", 4);
        metrics.put("stepCode", "EV_PLUS_OPPORTUNITY_MINING");
        metrics.put("totalEvaluatedMarkets", enriched.size());
        metrics.put("evPlusOpportunitiesFound", evPlusCount);
        metrics.put("evPlusRatePct", enriched.isEmpty() ? 0.0 : round4((evPlusCount * 100.0) / enriched.size()));
        metrics.put("minEvThresholdPct", round4(minEvThreshold * 100.0));
        metrics.put("maxEvThresholdPct", round4(maxEvThreshold * 100.0));
        metrics.put("averageEvAllMarketsPct", round4(allEvPctStats.getMean()));
        metrics.put("averageEvPlusOnlyPct", round4(evPlusOnlyStats.getN() > 0 ? evPlusOnlyStats.getMean() : 0.0));
        metrics.put("medianEvPlusOnlyPct", round4(evPlusOnlyStats.getN() > 0 ? evPlusOnlyStats.getPercentile(50) : 0.0));
        metrics.put("maxEvPlusPct", round4(evPlusOnlyStats.getN() > 0 ? evPlusOnlyStats.getMax() : 0.0));
        metrics.put("palpableErrorAlerts", palpableAlertCount);

        return new EvMiningBatchResult(enriched, metrics, topOpportunities);
    }

    private double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 10000.0) / 10000.0;
    }

    private double round6(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 1000000.0) / 1000000.0;
    }

    public record EvMiningBatchResult(
            List<MarketOddsRecordDto> records,
            Map<String, Object> metricsSummary,
            List<Map<String, Object>> topOpportunities
    ) {
    }
}
