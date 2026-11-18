package com.porros365.evmining.mining.engine;

import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paso 5 de la Metodologia (@Porros365 - Post 7/):
 * "EV+ NO significa ganar esa apuesta. Puedes perder 5 o 6 seguidas siendo todas de valor.
 *  El beneficio aparece con volumen: 500 apuestas a +5% con stake de 1u dan una expectativa de unas +25u.
 *  Por eso hacen falta stakes pequenos y disciplina."
 *
 * Implementa:
 * 1. Dimensionamiento de stake (FLAT_UNIT de 1u o FRACTIONAL_KELLY).
 * 2. Esperanza matematica analitica y varianza de cartera.
 * 3. Simulacion de Monte Carlo reproducible con Well19937c (Apache Commons Math) y randomSeed.
 */
@Component
public class StakeAndMonteCarloEngine {

    public StakeSimulationBatchResult executeStakingAndMonteCarlo(
            List<MarketOddsRecordDto> inputRecords,
            String stakingStrategy,
            double defaultUnitStake,
            double kellyFraction,
            double maxStakeUnits,
            int projectionVolumeBets,
            int simulationTrials,
            long randomSeed) {

        String normStrategy = (stakingStrategy != null && !stakingStrategy.isBlank())
                ? stakingStrategy.trim().toUpperCase(Locale.ROOT)
                : "FLAT_UNIT";

        List<MarketOddsRecordDto> enriched = new ArrayList<>(inputRecords.size());
        List<MarketOddsRecordDto> activeEvPlusBets = new ArrayList<>();

        double totalStakedDatasetUnits = 0.0;
        double totalExpectedProfitDatasetUnits = 0.0;
        double analyticalPortfolioVariance = 0.0;

        for (MarketOddsRecordDto r : inputRecords) {
            double trueProb = r.trueProbability() != null ? r.trueProbability() : (1.0 / r.sharpOddsSelection());
            double ev = r.expectedValue() != null ? r.expectedValue() : ((trueProb * r.softTakenOdds()) - 1.0);
            boolean isEvPlus = r.evPlusOpportunity() != null ? r.evPlusOpportunity() : (ev >= 0.0001);

            double stakeUnits = 0.0;
            double expProfitUnits = 0.0;

            if (isEvPlus && ev > 0.0) {
                if ("FRACTIONAL_KELLY".equalsIgnoreCase(normStrategy)) {
                    double b = r.softTakenOdds() - 1.0;
                    double fullKelly = b > 0 ? (ev / b) : 0.0;
                    stakeUnits = Math.min(maxStakeUnits, Math.max(0.1, fullKelly * kellyFraction * 100.0));
                } else {
                    stakeUnits = defaultUnitStake;
                }
                expProfitUnits = stakeUnits * ev;
                totalStakedDatasetUnits += stakeUnits;
                totalExpectedProfitDatasetUnits += expProfitUnits;

                // Bernoulli variance of single bet outcome: stake^2 * odds^2 * p * (1 - p)
                double singleBetVar = (stakeUnits * stakeUnits)
                        * (r.softTakenOdds() * r.softTakenOdds())
                        * trueProb * (1.0 - trueProb);
                analyticalPortfolioVariance += singleBetVar;
            }

            MarketOddsRecordDto updated = new MarketOddsRecordDto(
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
                    r.impliedProbabilityRaw(),
                    r.sharpMarginPct(),
                    round6(trueProb),
                    r.fairOdds() != null ? r.fairOdds() : round4(1.0 / trueProb),
                    round6(ev),
                    r.expectedValuePct() != null ? r.expectedValuePct() : round4(ev * 100.0),
                    isEvPlus,
                    round4(stakeUnits),
                    round4(expProfitUnits),
                    r.clvValue(),
                    r.clvPct(),
                    r.beatClosingLine(),
                    r.actualProfitUnits()
            );
            enriched.add(updated);
            if (isEvPlus && stakeUnits > 0.0) {
                activeEvPlusBets.add(updated);
            }
        }

        // Monte Carlo simulation over projectionVolumeBets (default 500 bets as in Post 7/)
        int effectiveVolume = Math.max(10, projectionVolumeBets);
        int effectiveTrials = Math.min(20000, Math.max(100, simulationTrials));

        Well19937c rng = new Well19937c(randomSeed);
        DescriptiveStatistics finalProfitStats = new DescriptiveStatistics();
        DescriptiveStatistics maxDrawdownStats = new DescriptiveStatistics();
        DescriptiveStatistics maxLosingStreakStats = new DescriptiveStatistics();

        int profitableTrials = 0;
        int trialsWithStreak5Plus = 0;
        int trialsWithStreak6Plus = 0;
        double[] meanTrajectory = new double[effectiveVolume];

        if (!activeEvPlusBets.isEmpty()) {
            int poolSize = activeEvPlusBets.size();
            for (int t = 0; t < effectiveTrials; t++) {
                double cumulativeProfit = 0.0;
                double peakProfit = 0.0;
                double maxDrawdown = 0.0;
                int currentLosingStreak = 0;
                int maxLosingStreak = 0;

                for (int b = 0; b < effectiveVolume; b++) {
                    MarketOddsRecordDto pick = activeEvPlusBets.get(b % poolSize);
                    double stake = pick.assignedStakeUnits();
                    double p = pick.trueProbability();
                    double odds = pick.softTakenOdds();

                    if (rng.nextDouble() < p) {
                        cumulativeProfit += stake * (odds - 1.0);
                        currentLosingStreak = 0;
                    } else {
                        cumulativeProfit -= stake;
                        currentLosingStreak++;
                        if (currentLosingStreak > maxLosingStreak) {
                            maxLosingStreak = currentLosingStreak;
                        }
                    }

                    if (cumulativeProfit > peakProfit) {
                        peakProfit = cumulativeProfit;
                    }
                    double dd = peakProfit - cumulativeProfit;
                    if (dd > maxDrawdown) {
                        maxDrawdown = dd;
                    }
                    meanTrajectory[b] += cumulativeProfit;
                }

                finalProfitStats.addValue(cumulativeProfit);
                maxDrawdownStats.addValue(maxDrawdown);
                maxLosingStreakStats.addValue(maxLosingStreak);

                if (cumulativeProfit > 0.0) {
                    profitableTrials++;
                }
                if (maxLosingStreak >= 5) {
                    trialsWithStreak5Plus++;
                }
                if (maxLosingStreak >= 6) {
                    trialsWithStreak6Plus++;
                }
            }
        }

        // Sampled trajectory points for frontend visualization (every 25 bets)
        List<Map<String, Object>> trajectoryCurve = new ArrayList<>();
        if (!activeEvPlusBets.isEmpty()) {
            double avgEvPerBetUnits = totalExpectedProfitDatasetUnits / activeEvPlusBets.size();
            int stepStride = Math.max(1, effectiveVolume / 20);
            for (int b = 0; b < effectiveVolume; b++) {
                if ((b + 1) % stepStride == 0 || b == 0 || b == effectiveVolume - 1) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("betNumber", b + 1);
                    point.put("analyticalExpectedProfitUnits", round4((b + 1) * avgEvPerBetUnits));
                    point.put("monteCarloMeanProfitUnits", round4(meanTrajectory[b] / effectiveTrials));
                    trajectoryCurve.add(point);
                }
            }
        }

        double projectedAnalyticalProfit = activeEvPlusBets.isEmpty()
                ? 0.0
                : (totalExpectedProfitDatasetUnits / activeEvPlusBets.size()) * effectiveVolume;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("stepNumber", 5);
        metrics.put("stepCode", "STAKE_AND_VOLUME_SIMULATION");
        metrics.put("stakingStrategy", normStrategy);
        metrics.put("defaultUnitStake", defaultUnitStake);
        metrics.put("randomSeed", randomSeed);
        metrics.put("activeEvPlusBetsInDataset", activeEvPlusBets.size());
        metrics.put("datasetTotalStakedUnits", round4(totalStakedDatasetUnits));
        metrics.put("datasetExpectedProfitUnits", round4(totalExpectedProfitDatasetUnits));
        metrics.put("datasetExpectedYieldPct", totalStakedDatasetUnits > 0
                ? round4((totalExpectedProfitDatasetUnits / totalStakedDatasetUnits) * 100.0) : 0.0);
        metrics.put("datasetAnalyticalStdDevUnits", round4(Math.sqrt(analyticalPortfolioVariance)));
        metrics.put("projectionVolumeBets", effectiveVolume);
        metrics.put("simulationTrials", effectiveTrials);
        metrics.put("projectedAnalyticalExpectedProfitUnits", round4(projectedAnalyticalProfit));
        metrics.put("monteCarloMeanProfitUnits", round4(finalProfitStats.getN() > 0 ? finalProfitStats.getMean() : 0.0));
        metrics.put("monteCarloMedianProfitUnits", round4(finalProfitStats.getN() > 0 ? finalProfitStats.getPercentile(50) : 0.0));
        metrics.put("monteCarloStdDevUnits", round4(finalProfitStats.getN() > 0 ? finalProfitStats.getStandardDeviation() : 0.0));
        metrics.put("monteCarloProfit5thPercentileUnits", round4(finalProfitStats.getN() > 0 ? finalProfitStats.getPercentile(5) : 0.0));
        metrics.put("monteCarloProfit95thPercentileUnits", round4(finalProfitStats.getN() > 0 ? finalProfitStats.getPercentile(95) : 0.0));
        metrics.put("probabilityOfPositiveProfitPct", effectiveTrials > 0 ? round4((profitableTrials * 100.0) / effectiveTrials) : 0.0);
        metrics.put("probabilityOfLosingStreak5PlusPct", effectiveTrials > 0 ? round4((trialsWithStreak5Plus * 100.0) / effectiveTrials) : 0.0);
        metrics.put("probabilityOfLosingStreak6PlusPct", effectiveTrials > 0 ? round4((trialsWithStreak6Plus * 100.0) / effectiveTrials) : 0.0);
        metrics.put("averageMaxLosingStreak", round4(maxLosingStreakStats.getN() > 0 ? maxLosingStreakStats.getMean() : 0.0));
        metrics.put("averageMaxDrawdownUnits", round4(maxDrawdownStats.getN() > 0 ? maxDrawdownStats.getMean() : 0.0));
        metrics.put("maxDrawdown95thPercentileUnits", round4(maxDrawdownStats.getN() > 0 ? maxDrawdownStats.getPercentile(95) : 0.0));

        return new StakeSimulationBatchResult(enriched, metrics, trajectoryCurve);
    }

    private double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 10000.0) / 10000.0;
    }

    private double round6(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 1000000.0) / 1000000.0;
    }

    public record StakeSimulationBatchResult(
            List<MarketOddsRecordDto> records,
            Map<String, Object> metricsSummary,
            List<Map<String, Object>> projectionTrajectory
    ) {
    }
}
