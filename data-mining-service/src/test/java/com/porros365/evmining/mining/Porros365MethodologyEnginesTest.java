package com.porros365.evmining.mining;

import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import com.porros365.evmining.mining.engine.ClvPerformanceAuditEngine;
import com.porros365.evmining.mining.engine.EvPlusOpportunityEngine;
import com.porros365.evmining.mining.engine.SharpDeviggingEngine;
import com.porros365.evmining.mining.engine.StakeAndMonteCarloEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Porros365MethodologyEnginesTest {

    private final SharpDeviggingEngine devigEngine = new SharpDeviggingEngine();
    private final EvPlusOpportunityEngine evEngine = new EvPlusOpportunityEngine();
    private final StakeAndMonteCarloEngine stakeEngine = new StakeAndMonteCarloEngine();
    private final ClvPerformanceAuditEngine clvEngine = new ClvPerformanceAuditEngine();

    @Test
    void shouldVerifyPost5And6CanonicalExampleAnd500BetsExpectedProfit() {
        // Post 5/: Sharp odds 1.90 / 2.00 -> 1/1.90 = 52.63%, 1/2.00 = 50.0% -> sum 102.63% (margin 2.63%)
        // Fair probability = 51.28% (~51.3%), Fair odds = 1.95
        // Post 6/: Soft bookmaker Bet365 pays 2.05 -> EV = 0.51282 * 2.05 - 1 = +5.13% (~+5%)
        MarketOddsRecordDto post5And6 = new MarketOddsRecordDto(
                "POST_5_6_CANONICAL", "FOOTBALL", "Premier League", "Arsenal vs Chelsea",
                "ASIAN_HANDICAP", "0.0", "Arsenal DNB", "SBOBET",
                1.90, 2.00, null, "BET365", 2.05, 1.87, "WON",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        SharpDeviggingEngine.DevigBatchResult step3 = devigEngine.executeDevigging(List.of(post5And6), "MULTIPLICATIVE");
        MarketOddsRecordDto devigged = step3.records().get(0);

        assertEquals(2.6316, devigged.sharpMarginPct(), 0.01);
        assertEquals(0.512821, devigged.trueProbability(), 0.001);
        assertEquals(1.95, devigged.fairOdds(), 0.01);

        EvPlusOpportunityEngine.EvMiningBatchResult step4 = evEngine.mineEvPlusOpportunities(step3.records(), 0.02, 0.35);
        MarketOddsRecordDto evMined = step4.records().get(0);

        assertTrue(evMined.evPlusOpportunity());
        assertEquals(5.1283, evMined.expectedValuePct(), 0.05);

        // Post 7/: 500 bets at ~+5.13% with 1u flat stake -> analytical expectation ~ +25.64u (~+25u)
        StakeAndMonteCarloEngine.StakeSimulationBatchResult step5 = stakeEngine.executeStakingAndMonteCarlo(
                step4.records(), "FLAT_UNIT", 1.0, 0.15, 2.0, 500, 3000, 42L
        );

        Map<String, Object> step5Metrics = step5.metricsSummary();
        double projectedAnalytical = ((Number) step5Metrics.get("projectedAnalyticalExpectedProfitUnits")).doubleValue();
        double monteCarloMean = ((Number) step5Metrics.get("monteCarloMeanProfitUnits")).doubleValue();
        double losingStreak5PlusProb = ((Number) step5Metrics.get("probabilityOfLosingStreak5PlusPct")).doubleValue();

        assertEquals(25.64, projectedAnalytical, 0.5);
        // Monte Carlo mean with 3000 trials must converge close to +25.6u
        assertEquals(projectedAnalytical, monteCarloMean, 2.5);
        // Post 7/: "Puedes perder 5 o 6 seguidas siendo todas de valor" -> in 500 bets at p=51.3%, prob of >=5 losses in a row is > 95%
        assertTrue(losingStreak5PlusProb > 90.0);
    }

    @Test
    void shouldVerifyPost10MonzaAtalantaAndPost11BosniaRomaniaRealPicks() {
        // Post 10/: Monza vs Atalanta -0.25 -> taken at 1.95 (Bet365), fair odds ~1.88 (EV +3.9%), closing 1.71 (CLV +14.1%), WON
        MarketOddsRecordDto monzaAtalanta = new MarketOddsRecordDto(
                "POST10_MONZA_ATALANTA", "FOOTBALL", "Serie A", "Monza vs Atalanta",
                "ASIAN_HANDICAP", "-0.25", "Atalanta -0.25", "SINGBET",
                1.83, 2.09, null, "BET365", 1.95, 1.71, "WON",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        // Post 11/: Bosnia vs Rumania -> taken at 4.60 (Paf), fair odds ~4.22 (EV +9.1%), closing 3.44 (CLV +33.6%), LOST
        MarketOddsRecordDto bosniaRomania = new MarketOddsRecordDto(
                "POST11_BOSNIA_ROMANIA", "FOOTBALL", "UEFA Nations League", "Bosnia vs Rumania",
                "MATCH_ODDS_1X2", "1X2", "Gana Rumania", "BETFAIR_EXCHANGE",
                4.11, 1.98, 3.63, "PAF", 4.60, 3.44, "LOST",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        var step3 = devigEngine.executeDevigging(List.of(monzaAtalanta, bosniaRomania), "MULTIPLICATIVE");
        var step4 = evEngine.mineEvPlusOpportunities(step3.records(), 0.02, 0.35);
        var step5 = stakeEngine.executeStakingAndMonteCarlo(step4.records(), "FLAT_UNIT", 1.0, 0.15, 2.0, 500, 1000, 42L);
        var step6 = clvEngine.executeClvAudit(step5.records(), 0.05);

        MarketOddsRecordDto auditedMonza = step6.records().get(0);
        MarketOddsRecordDto auditedRomania = step6.records().get(1);

        // Verify Monza vs Atalanta (Post 10/)
        assertEquals(1.88, auditedMonza.fairOdds(), 0.01);
        assertEquals(3.9, auditedMonza.expectedValuePct(), 0.25);
        assertEquals(14.04, auditedMonza.clvPct(), 0.15);
        assertTrue(auditedMonza.beatClosingLine());
        assertEquals(0.95, auditedMonza.actualProfitUnits(), 0.001);

        // Verify Bosnia vs Rumania (Post 11/)
        assertEquals(4.22, auditedRomania.fairOdds(), 0.02);
        assertEquals(9.1, auditedRomania.expectedValuePct(), 0.25);
        assertEquals(33.72, auditedRomania.clvPct(), 0.20);
        assertTrue(auditedRomania.beatClosingLine());
        assertEquals(-1.0, auditedRomania.actualProfitUnits(), 0.001);

        assertNotNull(step6.metricsSummary().get("beatClosingLineRateEvPlusPct"));
        assertEquals(100.0, ((Number) step6.metricsSummary().get("beatClosingLineRateEvPlusPct")).doubleValue(), 0.01);
    }
}
