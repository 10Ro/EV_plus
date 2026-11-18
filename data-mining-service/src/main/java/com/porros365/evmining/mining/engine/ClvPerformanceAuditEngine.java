package com.porros365.evmining.mining.engine;

import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paso 6 de la Metodologia (@Porros365 - Posts 8/, 9/, 10/, 11/, 12/):
 * Auditoria de CLV (Closing Line Value):
 *   CLV = (cuota_tomada / cuota_cierre) - 1
 * Ejemplos del hilo:
 *   - Post 8/: tomada 4.50, cierre 3.93 -> CLV +14.5%
 *   - Post 10/ (Monza vs Atalanta): tomada 1.95, cierre 1.71 -> CLV +14.04% (+14.1%)
 *   - Post 11/ (Bosnia vs Rumania): tomada 4.60, cierre 3.44 -> CLV +33.72% (+33.6%)
 *
 * Incluye inferencia estadistica con Apache Commons Math:
 *   - Correlacion de Pearson r(EV, CLV)
 *   - Regresion lineal simple (EV -> CLV)
 *   - Contraste t de Student unilateral H0: mu_CLV <= 0 vs H1: mu_CLV > 0
 */
@Component
public class ClvPerformanceAuditEngine {

    public ClvAuditBatchResult executeClvAudit(
            List<MarketOddsRecordDto> inputRecords,
            double significanceLevelAlpha) {

        List<MarketOddsRecordDto> enriched = new ArrayList<>(inputRecords.size());
        DescriptiveStatistics clvAllStats = new DescriptiveStatistics();
        DescriptiveStatistics clvEvPlusStats = new DescriptiveStatistics();

        List<Double> evPctList = new ArrayList<>();
        List<Double> clvPctList = new ArrayList<>();

        int evaluatedWithClosingCount = 0;
        int beatClosingCountAll = 0;
        int evPlusWithClosingCount = 0;
        int beatClosingCountEvPlus = 0;

        int wonCount = 0;
        int lostCount = 0;
        double actualProfitUnits = 0.0;
        double expectedProfitUnits = 0.0;
        double totalStakedUnits = 0.0;

        List<Map<String, Object>> pickAuditBreakdown = new ArrayList<>();

        for (MarketOddsRecordDto r : inputRecords) {
            double trueProb = r.trueProbability() != null ? r.trueProbability() : (1.0 / r.sharpOddsSelection());
            double ev = r.expectedValue() != null ? r.expectedValue() : ((trueProb * r.softTakenOdds()) - 1.0);
            double evPct = r.expectedValuePct() != null ? r.expectedValuePct() : (ev * 100.0);
            boolean isEvPlus = r.evPlusOpportunity() != null ? r.evPlusOpportunity() : (ev >= 0.0001);
            double stake = r.assignedStakeUnits() != null ? r.assignedStakeUnits() : (isEvPlus ? 1.0 : 0.0);
            double expProfit = r.expectedProfitUnits() != null ? r.expectedProfitUnits() : (stake * ev);

            Double clvVal = null;
            Double clvPct = null;
            Boolean beatClosing = null;

            if (r.closingFairOdds() != null && r.closingFairOdds() > 1.0) {
                clvVal = (r.softTakenOdds() / r.closingFairOdds()) - 1.0;
                clvPct = clvVal * 100.0;
                beatClosing = clvVal > 0.0;

                evaluatedWithClosingCount++;
                clvAllStats.addValue(clvPct);
                evPctList.add(evPct);
                clvPctList.add(clvPct);

                if (beatClosing) {
                    beatClosingCountAll++;
                }
                if (isEvPlus) {
                    evPlusWithClosingCount++;
                    clvEvPlusStats.addValue(clvPct);
                    if (beatClosing) {
                        beatClosingCountEvPlus++;
                    }
                }
            }

            Double singleActualProfit = 0.0;
            if (isEvPlus && stake > 0.0) {
                totalStakedUnits += stake;
                expectedProfitUnits += expProfit;
                if ("WON".equalsIgnoreCase(r.outcomeStatus())) {
                    wonCount++;
                    singleActualProfit = stake * (r.softTakenOdds() - 1.0);
                    actualProfitUnits += singleActualProfit;
                } else if ("LOST".equalsIgnoreCase(r.outcomeStatus())) {
                    lostCount++;
                    singleActualProfit = -stake;
                    actualProfitUnits += singleActualProfit;
                }
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
                    round4(evPct),
                    isEvPlus,
                    round4(stake),
                    round4(expProfit),
                    clvVal != null ? round6(clvVal) : null,
                    clvPct != null ? round4(clvPct) : null,
                    beatClosing,
                    round4(singleActualProfit)
            );
            enriched.add(updated);

            Map<String, Object> auditRow = new LinkedHashMap<>();
            auditRow.put("eventId", updated.eventId());
            auditRow.put("eventName", updated.eventName());
            auditRow.put("selectionName", updated.selectionName());
            auditRow.put("softBookmaker", updated.softBookmaker());
            auditRow.put("softTakenOdds", updated.softTakenOdds());
            auditRow.put("fairOdds", updated.fairOdds());
            auditRow.put("closingFairOdds", updated.closingFairOdds());
            auditRow.put("expectedValuePct", updated.expectedValuePct());
            auditRow.put("clvPct", updated.clvPct());
            auditRow.put("beatClosingLine", updated.beatClosingLine());
            auditRow.put("evPlusOpportunity", updated.evPlusOpportunity());
            auditRow.put("outcomeStatus", updated.outcomeStatus());
            auditRow.put("assignedStakeUnits", updated.assignedStakeUnits());
            auditRow.put("expectedProfitUnits", updated.expectedProfitUnits());
            auditRow.put("actualProfitUnits", updated.actualProfitUnits());
            pickAuditBreakdown.add(auditRow);
        }

        // Statistical inference: Pearson correlation, Linear Regression (EV -> CLV) & One-sample Student t-Test
        double pearsonCorrelation = 0.0;
        double regressionSlope = 0.0;
        double regressionIntercept = 0.0;
        double tStatistic = 0.0;
        double oneSidedPValue = 1.0;
        boolean statisticallySignificantAdvantage = false;

        if (evPctList.size() >= 2) {
            double[] evArr = evPctList.stream().mapToDouble(Double::doubleValue).toArray();
            double[] clvArr = clvPctList.stream().mapToDouble(Double::doubleValue).toArray();

            try {
                pearsonCorrelation = new PearsonsCorrelation().correlation(evArr, clvArr);
            } catch (Exception ignored) {
                pearsonCorrelation = 0.0;
            }

            SimpleRegression regression = new SimpleRegression();
            for (int i = 0; i < evArr.length; i++) {
                regression.addData(evArr[i], clvArr[i]);
            }
            regressionSlope = regression.getSlope();
            regressionIntercept = regression.getIntercept();
        }

        double[] targetClvSample = clvEvPlusStats.getN() >= 2 ? clvEvPlusStats.getValues() : clvAllStats.getValues();
        if (targetClvSample.length >= 2) {
            TTest tTest = new TTest();
            tStatistic = tTest.t(0.0, targetClvSample);
            double twoSidedP = tTest.tTest(0.0, targetClvSample);
            oneSidedPValue = tStatistic > 0 ? (twoSidedP / 2.0) : (1.0 - (twoSidedP / 2.0));
            statisticallySignificantAdvantage = (tStatistic > 0) && (oneSidedPValue < significanceLevelAlpha);
        }

        int settledEvPlusBets = wonCount + lostCount;
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("stepNumber", 6);
        metrics.put("stepCode", "CLV_AND_PERFORMANCE_AUDIT");
        metrics.put("evaluatedMarketsWithClosingOdds", evaluatedWithClosingCount);
        metrics.put("evPlusMarketsWithClosingOdds", evPlusWithClosingCount);
        metrics.put("averageClvAllMarketsPct", round4(clvAllStats.getN() > 0 ? clvAllStats.getMean() : 0.0));
        metrics.put("averageClvEvPlusOnlyPct", round4(clvEvPlusStats.getN() > 0 ? clvEvPlusStats.getMean() : 0.0));
        metrics.put("medianClvEvPlusOnlyPct", round4(clvEvPlusStats.getN() > 0 ? clvEvPlusStats.getPercentile(50) : 0.0));
        metrics.put("beatClosingLineRateAllPct", evaluatedWithClosingCount > 0
                ? round4((beatClosingCountAll * 100.0) / evaluatedWithClosingCount) : 0.0);
        metrics.put("beatClosingLineRateEvPlusPct", evPlusWithClosingCount > 0
                ? round4((beatClosingCountEvPlus * 100.0) / evPlusWithClosingCount) : 0.0);
        metrics.put("settledEvPlusBets", settledEvPlusBets);
        metrics.put("wonBets", wonCount);
        metrics.put("lostBets", lostCount);
        metrics.put("hitRatePct", settledEvPlusBets > 0 ? round4((wonCount * 100.0) / settledEvPlusBets) : 0.0);
        metrics.put("totalStakedUnits", round4(totalStakedUnits));
        metrics.put("expectedProfitUnits", round4(expectedProfitUnits));
        metrics.put("actualProfitUnits", round4(actualProfitUnits));
        metrics.put("expectedYieldPct", totalStakedUnits > 0 ? round4((expectedProfitUnits / totalStakedUnits) * 100.0) : 0.0);
        metrics.put("actualYieldPct", totalStakedUnits > 0 ? round4((actualProfitUnits / totalStakedUnits) * 100.0) : 0.0);
        metrics.put("pearsonCorrelationEvToClv", round4(pearsonCorrelation));
        metrics.put("regressionSlopeEvToClv", round4(regressionSlope));
        metrics.put("regressionInterceptEvToClv", round4(regressionIntercept));
        metrics.put("studentTStatistic", round4(tStatistic));
        metrics.put("studentTOneSidedPValue", round6(oneSidedPValue));
        metrics.put("significanceLevelAlpha", significanceLevelAlpha);
        metrics.put("statisticallySignificantMarketEdge", statisticallySignificantAdvantage);

        return new ClvAuditBatchResult(enriched, metrics, pickAuditBreakdown);
    }

    private double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 10000.0) / 10000.0;
    }

    private double round6(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 1000000.0) / 1000000.0;
    }

    public record ClvAuditBatchResult(
            List<MarketOddsRecordDto> records,
            Map<String, Object> metricsSummary,
            List<Map<String, Object>> pickAuditBreakdown
    ) {
    }
}
