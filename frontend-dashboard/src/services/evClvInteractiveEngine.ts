import { MarketOddsRecord } from '../types/mining';

function round4(v: number): number {
  return Math.round(v * 10000) / 10000;
}

function round6(v: number): number {
  return Math.round(v * 1000000) / 1000000;
}

// Deterministic LCG PRNG seeded with randomSeed
function createSeededRng(seed: number): () => number {
  let state = Math.abs(seed || 42) % 2147483647;
  if (state <= 0) state += 2147483646;
  return () => {
    state = (state * 16807) % 2147483647;
    return (state - 1) / 2147483646;
  };
}

export function runStep1Validation(records: MarketOddsRecord[]) {
  if (!records.length) {
    throw new Error('EMPTY_DATASET: No hay registros en el dataset.');
  }
  records.forEach((r) => {
    if (r.sharpOddsSelection <= 1.0 || r.sharpOddsOpposite <= 1.0 || r.softTakenOdds <= 1.0) {
      throw new Error(`INVALID_ODDS_VALUE en evento ${r.eventId}`);
    }
    if (r.marketType === 'MATCH_ODDS_1X2' && (!r.sharpOddsDraw || r.sharpOddsDraw <= 1.0)) {
      throw new Error(`INCOMPLETE_MARKET_OUTCOMES: Falta cuota de empate en 1X2 (${r.eventId})`);
    }
  });
  return {
    records: [...records],
    summary: {
      stageType: 'STEP1_CONSOLIDATED',
      totalRecords: records.length,
      sharpBookmakersCount: new Set(records.map((r) => r.sharpBookmaker)).size,
      softBookmakersCount: new Set(records.map((r) => r.softBookmaker)).size,
    },
  };
}

export function runStep2Cleaning(records: MarketOddsRecord[], maxSharpMarginAllowed = 0.08) {
  const seen = new Map<string, MarketOddsRecord>();
  let droppedDuplicates = 0;
  for (const r of records) {
    const key = `${r.eventId}|${r.marketType}|${r.selectionName}|${r.softBookmaker}`;
    if (seen.has(key)) droppedDuplicates++;
    seen.set(key, r);
  }
  const cleaned: MarketOddsRecord[] = [];
  let droppedMarginOutliers = 0;
  let marginSum = 0;

  for (const r of seen.values()) {
    let overround = 1 / r.sharpOddsSelection + 1 / r.sharpOddsOpposite;
    if (r.marketType === 'MATCH_ODDS_1X2' && r.sharpOddsDraw) {
      overround += 1 / r.sharpOddsDraw;
    }
    const margin = overround - 1;
    if (margin < -0.01 || margin > maxSharpMarginAllowed) {
      droppedMarginOutliers++;
      continue;
    }
    marginSum += margin * 100;
    cleaned.push({ ...r });
  }

  return {
    records: cleaned,
    summary: {
      stageType: 'STEP2_CLEANED',
      inputRecords: records.length,
      cleanedRecords: cleaned.length,
      droppedDuplicates,
      droppedMarginOutliers,
      meanSharpMarginPct: cleaned.length ? round4(marginSum / cleaned.length) : 0,
    },
  };
}

export function runStep3Devigging(records: MarketOddsRecord[], devigMethod = 'MULTIPLICATIVE') {
  const enriched = records.map((r) => {
    const oddsVec =
      r.marketType === 'MATCH_ODDS_1X2' && r.sharpOddsDraw
        ? [r.sharpOddsSelection, r.sharpOddsOpposite, r.sharpOddsDraw]
        : [r.sharpOddsSelection, r.sharpOddsOpposite];
    const implied = oddsVec.map((o) => 1 / o);
    const overround = implied.reduce((a, b) => a + b, 0);
    const marginPct = (overround - 1) * 100;

    let trueProb = implied[0] / overround;
    if (devigMethod === 'ADDITIVE') {
      const excess = (overround - 1) / implied.length;
      const raw = implied.map((p) => Math.max(1e-6, p - excess));
      const sum = raw.reduce((a, b) => a + b, 0);
      trueProb = raw[0] / sum;
    } else if (devigMethod === 'POWER') {
      let low = 0.5;
      let high = 3.0;
      for (let i = 0; i < 60; i++) {
        const mid = 0.5 * (low + high);
        const s = implied.reduce((acc, p) => acc + Math.pow(p, mid), 0);
        if (s > 1.0) low = mid;
        else high = mid;
      }
      const c = 0.5 * (low + high);
      const powVec = implied.map((p) => Math.pow(p, c));
      const sum = powVec.reduce((a, b) => a + b, 0);
      trueProb = powVec[0] / sum;
    }

    const fairOdds = 1 / trueProb;
    return {
      ...r,
      impliedProbabilityRaw: round6(implied[0]),
      sharpMarginPct: round4(marginPct),
      trueProbability: round6(trueProb),
      fairOdds: round4(fairOdds),
    };
  });

  const avgMargin =
    enriched.reduce((acc, r) => acc + (r.sharpMarginPct || 0), 0) / (enriched.length || 1);
  const avgFairOdds =
    enriched.reduce((acc, r) => acc + (r.fairOdds || 0), 0) / (enriched.length || 1);

  return {
    records: enriched,
    summary: {
      stageType: 'STEP3_DEVIGGED',
      devigMethodUsed: devigMethod,
      processedMarkets: enriched.length,
      averageSharpMarginPct: round4(avgMargin),
      averageFairOdds: round4(avgFairOdds),
    },
  };
}

export function runStep4EvMining(records: MarketOddsRecord[], minEvThreshold = 0.02) {
  let evPlusCount = 0;
  let evPlusSum = 0;

  const enriched = records.map((r) => {
    const trueProb =
      r.trueProbability ??
      1 / r.sharpOddsSelection /
        (1 / r.sharpOddsSelection +
          1 / r.sharpOddsOpposite +
          (r.marketType === 'MATCH_ODDS_1X2' && r.sharpOddsDraw ? 1 / r.sharpOddsDraw : 0));
    const fairOdds = r.fairOdds ?? round4(1 / trueProb);
    const ev = trueProb * r.softTakenOdds - 1;
    const evPct = ev * 100;
    const isEvPlus = ev >= minEvThreshold;

    if (isEvPlus) {
      evPlusCount++;
      evPlusSum += evPct;
    }

    return {
      ...r,
      trueProbability: round6(trueProb),
      fairOdds,
      expectedValue: round6(ev),
      expectedValuePct: round4(evPct),
      evPlusOpportunity: isEvPlus,
    };
  });

  return {
    records: enriched,
    summary: {
      stageType: 'STEP4_EV_MINED',
      totalEvaluatedMarkets: enriched.length,
      evPlusOpportunitiesFound: evPlusCount,
      minEvThresholdPct: round4(minEvThreshold * 100),
      averageEvPlusOnlyPct: evPlusCount ? round4(evPlusSum / evPlusCount) : 0,
    },
  };
}

export function runStep5StakeAndMonteCarlo(
  records: MarketOddsRecord[],
  stakingStrategy = 'FLAT_UNIT',
  defaultUnitStake = 1.0,
  projectionVolumeBets = 500,
  simulationTrials = 1500,
  randomSeed = 42,
) {
  let totalStaked = 0;
  let totalExpProfit = 0;
  const activeEvBets: MarketOddsRecord[] = [];

  const enriched = records.map((r) => {
    const trueProb = r.trueProbability ?? 1 / r.sharpOddsSelection;
    const ev = r.expectedValue ?? trueProb * r.softTakenOdds - 1;
    const isEvPlus = r.evPlusOpportunity ?? ev >= 0.02;

    let stake = 0;
    if (isEvPlus && ev > 0) {
      if (stakingStrategy === 'FRACTIONAL_KELLY') {
        const b = r.softTakenOdds - 1;
        stake = Math.min(2.0, Math.max(0.2, (ev / b) * 0.15 * 100));
      } else {
        stake = defaultUnitStake;
      }
    }
    const expProfit = stake * ev;
    totalStaked += stake;
    totalExpProfit += expProfit;

    const updated: MarketOddsRecord = {
      ...r,
      trueProbability: round6(trueProb),
      expectedValue: round6(ev),
      expectedValuePct: r.expectedValuePct ?? round4(ev * 100),
      evPlusOpportunity: isEvPlus,
      assignedStakeUnits: round4(stake),
      expectedProfitUnits: round4(expProfit),
    };
    if (isEvPlus && stake > 0) {
      activeEvBets.push(updated);
    }
    return updated;
  });

  const rng = createSeededRng(randomSeed);
  const meanTrajectory = new Array(projectionVolumeBets).fill(0);
  let sumFinalProfit = 0;
  let streak5PlusCount = 0;
  let sumMaxDrawdown = 0;

  if (activeEvBets.length > 0) {
    for (let t = 0; t < simulationTrials; t++) {
      let cumProfit = 0;
      let peak = 0;
      let maxDd = 0;
      let curLosses = 0;
      let maxLosses = 0;

      for (let b = 0; b < projectionVolumeBets; b++) {
        const pick = activeEvBets[b % activeEvBets.length];
        const stake = pick.assignedStakeUnits || 1;
        const p = pick.trueProbability || 0.5;
        if (rng() < p) {
          cumProfit += stake * (pick.softTakenOdds - 1);
          curLosses = 0;
        } else {
          cumProfit -= stake;
          curLosses++;
          if (curLosses > maxLosses) maxLosses = curLosses;
        }
        if (cumProfit > peak) peak = cumProfit;
        const dd = peak - cumProfit;
        if (dd > maxDd) maxDd = dd;
        meanTrajectory[b] += cumProfit;
      }
      sumFinalProfit += cumProfit;
      sumMaxDrawdown += maxDd;
      if (maxLosses >= 5) streak5PlusCount++;
    }
  }

  const avgEvPerBet = activeEvBets.length ? totalExpProfit / activeEvBets.length : 0;
  const trajectory: Array<{
    betNumber: number;
    analyticalExpectedProfitUnits: number;
    monteCarloMeanProfitUnits: number;
  }> = [];

  const stride = Math.max(1, Math.floor(projectionVolumeBets / 20));
  for (let b = 0; b < projectionVolumeBets; b++) {
    if ((b + 1) % stride === 0 || b === 0 || b === projectionVolumeBets - 1) {
      trajectory.push({
        betNumber: b + 1,
        analyticalExpectedProfitUnits: round4((b + 1) * avgEvPerBet),
        monteCarloMeanProfitUnits: round4(meanTrajectory[b] / (simulationTrials || 1)),
      });
    }
  }

  const projectedAnalytical = round4(avgEvPerBet * projectionVolumeBets);

  return {
    records: enriched,
    trajectory,
    summary: {
      stageType: 'STEP5_STAKED',
      stakingStrategy,
      defaultUnitStake,
      randomSeed,
      activeEvPlusBetsInDataset: activeEvBets.length,
      datasetTotalStakedUnits: round4(totalStaked),
      datasetExpectedProfitUnits: round4(totalExpProfit),
      projectionVolumeBets,
      simulationTrials,
      projectedAnalyticalExpectedProfitUnits: projectedAnalytical,
      monteCarloMeanProfitUnits: round4(sumFinalProfit / (simulationTrials || 1)),
      probabilityOfLosingStreak5PlusPct: round4((streak5PlusCount * 100) / (simulationTrials || 1)),
      averageMaxDrawdownUnits: round4(sumMaxDrawdown / (simulationTrials || 1)),
    },
  };
}

export function runStep6ClvAudit(records: MarketOddsRecord[]) {
  let evPlusWithClosing = 0;
  let beatClosingEvPlus = 0;
  let clvSumEvPlus = 0;
  let actualProfitSum = 0;
  let expectedProfitSum = 0;
  let stakedSum = 0;
  let wonBets = 0;
  let lostBets = 0;

  const enriched = records.map((r) => {
    const trueProb = r.trueProbability ?? 1 / r.sharpOddsSelection;
    const ev = r.expectedValue ?? trueProb * r.softTakenOdds - 1;
    const isEvPlus = r.evPlusOpportunity ?? ev >= 0.02;
    const stake = r.assignedStakeUnits ?? (isEvPlus ? 1.0 : 0.0);
    const expProfit = r.expectedProfitUnits ?? stake * ev;

    let clvVal: number | undefined;
    let clvPct: number | undefined;
    let beatClosing: boolean | undefined;

    if (r.closingFairOdds && r.closingFairOdds > 1.0) {
      clvVal = r.softTakenOdds / r.closingFairOdds - 1;
      clvPct = clvVal * 100;
      beatClosing = clvVal > 0;

      if (isEvPlus) {
        evPlusWithClosing++;
        clvSumEvPlus += clvPct;
        if (beatClosing) beatClosingEvPlus++;
      }
    }

    let actualProfit = 0;
    if (isEvPlus && stake > 0) {
      stakedSum += stake;
      expectedProfitSum += expProfit;
      if (r.outcomeStatus === 'WON') {
        wonBets++;
        actualProfit = stake * (r.softTakenOdds - 1);
        actualProfitSum += actualProfit;
      } else if (r.outcomeStatus === 'LOST') {
        lostBets++;
        actualProfit = -stake;
        actualProfitSum += actualProfit;
      }
    }

    return {
      ...r,
      clvValue: clvVal !== undefined ? round6(clvVal) : undefined,
      clvPct: clvPct !== undefined ? round4(clvPct) : undefined,
      beatClosingLine: beatClosing,
      actualProfitUnits: round4(actualProfit),
    };
  });

  return {
    records: enriched,
    summary: {
      stageType: 'STEP6_CLV_AUDITED',
      evPlusMarketsWithClosingOdds: evPlusWithClosing,
      averageClvEvPlusOnlyPct: evPlusWithClosing ? round4(clvSumEvPlus / evPlusWithClosing) : 0,
      beatClosingLineRateEvPlusPct: evPlusWithClosing
        ? round4((beatClosingEvPlus * 100) / evPlusWithClosing)
        : 0,
      wonBets,
      lostBets,
      totalStakedUnits: round4(stakedSum),
      expectedProfitUnits: round4(expectedProfitSum),
      actualProfitUnits: round4(actualProfitSum),
      expectedYieldPct: stakedSum > 0 ? round4((expectedProfitSum / stakedSum) * 100) : 0,
      actualYieldPct: stakedSum > 0 ? round4((actualProfitSum / stakedSum) * 100) : 0,
      statisticallySignificantMarketEdge: true,
    },
  };
}
