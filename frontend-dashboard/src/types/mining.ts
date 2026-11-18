export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface MarketOddsRecord {
  eventId: string;
  sport: string;
  competition: string;
  eventName: string;
  marketType: 'ASIAN_HANDICAP' | 'MATCH_ODDS_1X2' | 'OVER_UNDER';
  marketLine: string;
  selectionName: string;
  sharpBookmaker: string;
  sharpOddsSelection: number;
  sharpOddsOpposite: number;
  sharpOddsDraw: number | null;
  softBookmaker: string;
  softTakenOdds: number;
  closingFairOdds: number | null;
  outcomeStatus: 'WON' | 'LOST' | 'VOID' | 'PENDING';
  impliedProbabilityRaw?: number;
  sharpMarginPct?: number;
  trueProbability?: number;
  fairOdds?: number;
  expectedValue?: number;
  expectedValuePct?: number;
  evPlusOpportunity?: boolean;
  assignedStakeUnits?: number;
  expectedProfitUnits?: number;
  clvValue?: number;
  clvPct?: number;
  beatClosingLine?: boolean;
  actualProfitUnits?: number;
}

export interface DatasetSummary {
  id: string;
  name: string;
  description: string;
  sourceType: string;
  sourceReference: string;
  latestVersionId: string;
  latestVersionNumber: number;
  latestStageType: string;
  recordCount: number;
  createdAt: string;
}

export interface WorkflowStepSheet {
  stepNumber: number;
  stepCode: string;
  name: string;
  description: string;
  objective: string;
  targetService: string;
  algorithmCode: string;
  algorithmName: string;
  dependsOnStepNumber: number | null;
  inputContract: Record<string, unknown>;
  outputContract: Record<string, unknown>;
  defaultParameters: Record<string, unknown>;
  validationCriteria: string[];
  possibleErrors: string[];
  expectedResults: Record<string, unknown>;
}

export interface StepExecutionItem {
  id: string;
  stepNumber: number;
  stepCode: string;
  stepName: string;
  targetService: string;
  dependsOnStepNumber: number | null;
  status: ExecutionStatus;
  independentMode: boolean;
  attemptNumber: number;
  algorithmVersion: string;
  inputDatasetVersionId: string;
  outputDatasetVersionId: string | null;
  randomSeed: number;
  parametersUsed: Record<string, unknown>;
  outputSummary: Record<string, unknown>;
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
}

export interface ExecutionItem {
  id: string;
  workflowId: string;
  workflowCode: string;
  workflowName: string;
  methodologyVersion: string;
  initialDatasetVersionId: string;
  currentDatasetVersionId: string;
  configurationId: string;
  configurationName: string;
  randomSeed: number;
  codeVersion: string;
  executionMode: string;
  status: ExecutionStatus;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  createdAt: string;
  steps: StepExecutionItem[];
  recordsSnapshot: MarketOddsRecord[];
  monteCarloTrajectory: Array<{
    betNumber: number;
    analyticalExpectedProfitUnits: number;
    monteCarloMeanProfitUnits: number;
  }>;
}

export interface ExecutionLogItem {
  id: string;
  executionId: string;
  stepNumber: number | null;
  serviceName: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  eventCode: string;
  message: string;
  timestamp: string;
}
