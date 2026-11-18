package com.porros365.evmining.mining.engine;

import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paso 3 de la Metodologia (@Porros365 - Posts 4/ y 5/):
 * Eliminacion de margen (De-Vigging) de las casas Sharp (Betfair Exchange, SingBet, Sbobet, Circa)
 * para estimar la probabilidad real (p_real) y la cuota justa (Fair Odds = 1 / p_real).
 */
@Component
public class SharpDeviggingEngine {

    public DevigBatchResult executeDevigging(List<MarketOddsRecordDto> inputRecords, String devigMethod) {
        String normalizedMethod = (devigMethod != null && !devigMethod.isBlank())
                ? devigMethod.trim().toUpperCase(Locale.ROOT)
                : "MULTIPLICATIVE";

        List<MarketOddsRecordDto> enriched = new ArrayList<>(inputRecords.size());
        DescriptiveStatistics marginStats = new DescriptiveStatistics();
        DescriptiveStatistics fairOddsStats = new DescriptiveStatistics();
        DescriptiveStatistics trueProbStats = new DescriptiveStatistics();

        for (MarketOddsRecordDto r : inputRecords) {
            double[] rawOdds = extractMarketOddsVector(r);
            double[] rawImplied = new double[rawOdds.length];
            double overroundSum = 0.0;

            for (int i = 0; i < rawOdds.length; i++) {
                rawImplied[i] = 1.0 / rawOdds[i];
                overroundSum += rawImplied[i];
            }

            double marginPct = (overroundSum - 1.0) * 100.0;
            double[] trueProbs = computeTrueProbabilities(rawImplied, overroundSum, normalizedMethod);

            // Invariant verification: sum of true probabilities across all outcomes must equal 1.0
            double probSum = 0.0;
            for (double p : trueProbs) {
                probSum += p;
            }
            if (Math.abs(probSum - 1.0) > 1e-4) {
                throw new IllegalStateException("PROBABILITY_SUM_INVARIANT_VIOLATION at event " + r.eventId() + ": sum=" + probSum);
            }

            double selectionTrueProb = trueProbs[0];
            double selectionFairOdds = 1.0 / selectionTrueProb;

            marginStats.addValue(marginPct);
            fairOddsStats.addValue(selectionFairOdds);
            trueProbStats.addValue(selectionTrueProb * 100.0);

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
                    round6(rawImplied[0]),
                    round4(marginPct),
                    round6(selectionTrueProb),
                    round4(selectionFairOdds),
                    r.expectedValue(),
                    r.expectedValuePct(),
                    r.evPlusOpportunity(),
                    r.assignedStakeUnits(),
                    r.expectedProfitUnits(),
                    r.clvValue(),
                    r.clvPct(),
                    r.beatClosingLine(),
                    r.actualProfitUnits()
            ));
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("stepNumber", 3);
        metrics.put("stepCode", "SHARP_DEVIGGING_AND_FAIR_ODDS");
        metrics.put("devigMethodUsed", normalizedMethod);
        metrics.put("processedMarkets", enriched.size());
        metrics.put("averageSharpMarginPct", round4(marginStats.getMean()));
        metrics.put("medianSharpMarginPct", round4(marginStats.getPercentile(50)));
        metrics.put("minSharpMarginPct", round4(marginStats.getMin()));
        metrics.put("maxSharpMarginPct", round4(marginStats.getMax()));
        metrics.put("averageFairOdds", round4(fairOddsStats.getMean()));
        metrics.put("averageTrueProbabilityPct", round4(trueProbStats.getMean()));

        return new DevigBatchResult(enriched, metrics);
    }

    private double[] extractMarketOddsVector(MarketOddsRecordDto r) {
        if ("MATCH_ODDS_1X2".equalsIgnoreCase(r.marketType()) && r.sharpOddsDraw() != null) {
            return new double[]{r.sharpOddsSelection(), r.sharpOddsOpposite(), r.sharpOddsDraw()};
        }
        return new double[]{r.sharpOddsSelection(), r.sharpOddsOpposite()};
    }

    private double[] computeTrueProbabilities(double[] rawImplied, double overroundSum, String method) {
        int k = rawImplied.length;
        double[] trueProbs = new double[k];

        if ("ADDITIVE".equalsIgnoreCase(method)) {
            double excessPerOutcome = (overroundSum - 1.0) / k;
            double sum = 0.0;
            for (int i = 0; i < k; i++) {
                trueProbs[i] = Math.max(1e-6, rawImplied[i] - excessPerOutcome);
                sum += trueProbs[i];
            }
            for (int i = 0; i < k; i++) {
                trueProbs[i] /= sum;
            }
            return trueProbs;
        }

        if ("POWER".equalsIgnoreCase(method)) {
            // Power method: find exponent c > 0 such that sum(rawImplied[i]^c) = 1.0
            // Accounts for favorite-longshot bias in high odds (comment by @LauQuinos & Post 11 Romania 4.22)
            double low = 0.5;
            double high = 3.0;
            for (int iter = 0; iter < 80; iter++) {
                double mid = 0.5 * (low + high);
                double sum = 0.0;
                for (double p : rawImplied) {
                    sum += Math.pow(p, mid);
                }
                if (sum > 1.0) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            double c = 0.5 * (low + high);
            double sum = 0.0;
            for (int i = 0; i < k; i++) {
                trueProbs[i] = Math.pow(rawImplied[i], c);
                sum += trueProbs[i];
            }
            for (int i = 0; i < k; i++) {
                trueProbs[i] /= sum;
            }
            return trueProbs;
        }

        // Default: MULTIPLICATIVE (Proportional method from Post 5/ of @Porros365)
        for (int i = 0; i < k; i++) {
            trueProbs[i] = rawImplied[i] / overroundSum;
        }
        return trueProbs;
    }

    private double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 10000.0) / 10000.0;
    }

    private double round6(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 1000000.0) / 1000000.0;
    }

    public record DevigBatchResult(
            List<MarketOddsRecordDto> records,
            Map<String, Object> metricsSummary
    ) {
    }
}
