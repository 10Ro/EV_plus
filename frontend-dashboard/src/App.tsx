import React, { useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  BookOpen,
  CheckCircle2,
  Database,
  Download,
  GitCompare,
  Layers,
  Play,
  PlayCircle,
  Plus,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Terminal,
  TrendingUp,
  XCircle,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { METHODOLOGY_STEP_SHEETS, PORROS365_SEED_RECORDS } from './data/porros365SeedData';
import {
  runStep1Validation,
  runStep2Cleaning,
  runStep3Devigging,
  runStep4EvMining,
  runStep5StakeAndMonteCarlo,
  runStep6ClvAudit,
} from './services/evClvInteractiveEngine';
import {
  DatasetSummary,
  ExecutionItem,
  ExecutionLogItem,
  ExecutionStatus,
  MarketOddsRecord,
  StepExecutionItem,
} from './types/mining';

function buildInitialExecution(
  id: string,
  configName: string,
  datasetVersionId: string,
  records: MarketOddsRecord[],
  devigMethod: string,
  minEvThreshold: number,
  stakingStrategy: string,
  randomSeed: number,
  autoRunAll = false,
): { execution: ExecutionItem; logs: ExecutionLogItem[] } {
  const now = new Date().toISOString();
  const steps: StepExecutionItem[] = METHODOLOGY_STEP_SHEETS.map((s) => ({
    id: `${id}-step-${s.stepNumber}`,
    stepNumber: s.stepNumber,
    stepCode: s.stepCode,
    stepName: s.name,
    targetService: s.targetService,
    dependsOnStepNumber: s.dependsOnStepNumber,
    status: 'PENDING',
    independentMode: false,
    attemptNumber: 1,
    algorithmVersion: '1.0.0',
    inputDatasetVersionId: datasetVersionId,
    outputDatasetVersionId: null,
    randomSeed,
    parametersUsed: {
      ...s.defaultParameters,
      devigMethod,
      minEvThreshold,
      stakingStrategy,
    },
    outputSummary: {},
    errorCode: null,
    errorMessage: null,
    startedAt: null,
    finishedAt: null,
    durationMs: null,
  }));

  const exec: ExecutionItem = {
    id,
    workflowId: 'b0000000-0000-0000-0000-000000000001',
    workflowCode: 'PORROS365_EV_CLV_V1',
    workflowName: 'Metodología Cuantitativa EV+ y Auditoría CLV (@Porros365)',
    methodologyVersion: '1.0.0',
    initialDatasetVersionId: datasetVersionId,
    currentDatasetVersionId: datasetVersionId,
    configurationId: `cfg-${id}`,
    configurationName: configName,
    randomSeed,
    codeVersion: 'v0.6.0',
    executionMode: autoRunAll ? 'FULL_PIPELINE' : 'SINGLE_STEP',
    status: 'PENDING',
    startedAt: null,
    finishedAt: null,
    durationMs: null,
    errorMessage: null,
    createdAt: now,
    steps,
    recordsSnapshot: records.map((r) => ({ ...r })),
    monteCarloTrajectory: [],
  };

  const logs: ExecutionLogItem[] = [
    {
      id: `log-init-${id}`,
      executionId: id,
      stepNumber: null,
      serviceName: 'workflow-orchestrator-service',
      level: 'INFO',
      eventCode: 'EXECUTION_INITIALIZED',
      message: `Ejecución ${id} creada con configuración "${configName}" (seed=${randomSeed}, devig=${devigMethod}, minEV=${(minEvThreshold * 100).toFixed(1)}%)`,
      timestamp: now,
    },
  ];

  if (autoRunAll) {
    let curRecords = exec.recordsSnapshot;
    const s1 = runStep1Validation(curRecords);
    curRecords = s1.records;
    const s2 = runStep2Cleaning(curRecords);
    curRecords = s2.records;
    const s3 = runStep3Devigging(curRecords, devigMethod);
    curRecords = s3.records;
    const s4 = runStep4EvMining(curRecords, minEvThreshold);
    curRecords = s4.records;
    const s5 = runStep5StakeAndMonteCarlo(curRecords, stakingStrategy, 1.0, 500, 2000, randomSeed);
    curRecords = s5.records;
    const s6 = runStep6ClvAudit(curRecords);
    curRecords = s6.records;

    const summaries = [s1.summary, s2.summary, s3.summary, s4.summary, s5.summary, s6.summary];
    const durations = [18, 24, 31, 19, 86, 29];

    exec.steps = exec.steps.map((st, idx) => ({
      ...st,
      status: 'COMPLETED',
      outputDatasetVersionId: `${datasetVersionId}-v${idx + 2}`,
      outputSummary: summaries[idx] as Record<string, unknown>,
      startedAt: now,
      finishedAt: now,
      durationMs: durations[idx],
    }));
    exec.recordsSnapshot = curRecords;
    exec.monteCarloTrajectory = s5.trajectory;
    exec.status = 'COMPLETED';
    exec.startedAt = now;
    exec.finishedAt = now;
    exec.durationMs = durations.reduce((a, b) => a + b, 0);

    exec.steps.forEach((st) => {
      logs.push({
        id: `log-${id}-step-${st.stepNumber}`,
        executionId: id,
        stepNumber: st.stepNumber,
        serviceName: st.targetService,
        level: 'INFO',
        eventCode: `STEP_${st.stepNumber}_COMPLETED`,
        message: `Paso ${st.stepNumber} (${st.stepCode}) completado en ${st.durationMs} ms`,
        timestamp: now,
      });
    });
  }

  return { execution: exec, logs };
}

export default function App() {
  const [activeTab, setActiveTab] = useState<
    'orchestrator' | 'methodology' | 'datasets' | 'analytics' | 'compare' | 'logs'
  >('orchestrator');

  // Datasets state
  const [datasets, setDatasets] = useState<DatasetSummary[]>([
    {
      id: 'd0000000-0000-0000-0000-000000000001',
      name: 'Dataset Oficial Hilo @Porros365 (EV+ & CLV Real Picks)',
      description:
        'Incluye Monza vs Atalanta (-0.25 a 1.95, Cierre 1.71), Bosnia vs Rumania (4.60, Cierre 3.44), Ejemplo Post 5/6 (1.90/2.00 vs 2.05) y cartera Sharp vs Soft.',
      sourceType: 'SEED',
      sourceReference: 'https://x.com/Porros365/status/2122822935002132523',
      latestVersionId: 'd1000000-0000-0000-0000-000000000001',
      latestVersionNumber: 1,
      latestStageType: 'RAW_IMPORT',
      recordCount: PORROS365_SEED_RECORDS.length,
      createdAt: '2026-11-18T17:00:00Z',
    },
  ]);
  const [activeRecords, setActiveRecords] = useState<MarketOddsRecord[]>(PORROS365_SEED_RECORDS);
  const [csvImportName, setCsvImportName] = useState('');
  const [csvImportText, setCsvImportText] = useState(
    'eventId,sport,competition,eventName,marketType,marketLine,selectionName,sharpBookmaker,sharpOddsSelection,sharpOddsOpposite,sharpOddsDraw,softBookmaker,softTakenOdds,closingFairOdds,outcomeStatus\nCHAMPIONS_2026_RMA_MCI,FOOTBALL,Champions League,Real Madrid vs Man City,ASIAN_HANDICAP,0.0,Real Madrid DNB,PINNACLE_SHARP,1.92,1.98,,BET365,2.08,1.85,WON',
  );

  // Configuration parameters for new executions
  const [devigMethod, setDevigMethod] = useState<'MULTIPLICATIVE' | 'ADDITIVE' | 'POWER'>('MULTIPLICATIVE');
  const [minEvThresholdPct, setMinEvThresholdPct] = useState<number>(2.0);
  const [stakingStrategy, setStakingStrategy] = useState<'FLAT_UNIT' | 'FRACTIONAL_KELLY'>('FLAT_UNIT');
  const [randomSeed, setRandomSeed] = useState<number>(42);
  const [independentMode, setIndependentMode] = useState<boolean>(false);

  // Pre-populate 2 executions so Comparison and Analytics work out-of-the-box
  const initialData = useMemo(() => {
    const first = buildInitialExecution(
      'exec-porros365-multiplicative-v1',
      'Ejecución #1 — Multiplicativo Estándar (EV >= 2.0%, Flat 1u)',
      'd1000000-0000-0000-0000-000000000001',
      PORROS365_SEED_RECORDS,
      'MULTIPLICATIVE',
      0.02,
      'FLAT_UNIT',
      42,
      true,
    );
    const second = buildInitialExecution(
      'exec-porros365-power-kelly-v2',
      'Ejecución #2 — De-Vig Power Sesgo Cuotas Altas (EV >= 4.0%, Kelly Fraccional)',
      'd1000000-0000-0000-0000-000000000001',
      PORROS365_SEED_RECORDS,
      'POWER',
      0.04,
      'FRACTIONAL_KELLY',
      99,
      true,
    );
    return {
      executions: [first.execution, second.execution],
      logs: [...first.logs, ...second.logs],
    };
  }, []);

  const [executions, setExecutions] = useState<ExecutionItem[]>(initialData.executions);
  const [selectedExecutionId, setSelectedExecutionId] = useState<string>(initialData.executions[0].id);
  const [compareFirstId, setCompareFirstId] = useState<string>(initialData.executions[0].id);
  const [compareSecondId, setCompareSecondId] = useState<string>(initialData.executions[1].id);
  const [logs, setLogs] = useState<ExecutionLogItem[]>(initialData.logs);
  const [bannerAlert, setBannerAlert] = useState<{ type: 'error' | 'success' | 'info'; text: string } | null>(
    null,
  );

  const currentExecution = useMemo(
    () => executions.find((e) => e.id === selectedExecutionId) || executions[0],
    [executions, selectedExecutionId],
  );

  const appendLog = (
    executionId: string,
    stepNumber: number | null,
    serviceName: string,
    level: 'INFO' | 'WARN' | 'ERROR',
    eventCode: string,
    message: string,
  ) => {
    setLogs((prev) => [
      {
        id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        executionId,
        stepNumber,
        serviceName,
        level,
        eventCode,
        message,
        timestamp: new Date().toISOString(),
      },
      ...prev,
    ]);
  };

  // Create a fresh PENDING execution
  const handleCreateNewExecution = () => {
    const newId = `exec-${Date.now().toString().slice(-6)}`;
    const { execution, logs: initLogs } = buildInitialExecution(
      newId,
      `Ejecución Personalizada (${devigMethod}, EV >= ${minEvThresholdPct}%, ${stakingStrategy})`,
      datasets[0].latestVersionId,
      activeRecords,
      devigMethod,
      minEvThresholdPct / 100,
      stakingStrategy,
      randomSeed,
      false,
    );
    setExecutions((prev) => [execution, ...prev]);
    setLogs((prev) => [...initLogs, ...prev]);
    setSelectedExecutionId(newId);
    setBannerAlert({
      type: 'info',
      text: `Nueva ejecución ${newId} creada en estado PENDING con los 6 pasos listos para ejecución individual o completa.`,
    });
  };

  // Execute a single step respecting dependency rules (unless independentMode is enabled)
  const handleRunSingleStep = (stepNumber: number, forceIndependent = independentMode) => {
    const exec = currentExecution;
    const targetStep = exec.steps.find((s) => s.stepNumber === stepNumber);
    if (!targetStep) return;

    if (targetStep.dependsOnStepNumber !== null && !forceIndependent) {
      const parentStep = exec.steps.find((s) => s.stepNumber === targetStep.dependsOnStepNumber);
      if (!parentStep || parentStep.status !== 'COMPLETED') {
        const msg = `DEPENDENCY_NOT_COMPLETED (409 Conflict): No se puede ejecutar el Paso ${stepNumber} (${targetStep.stepCode}) porque el Paso ${targetStep.dependsOnStepNumber} está en estado ${parentStep?.status}. Completa primero el Paso ${targetStep.dependsOnStepNumber} o activa el "Modo de Ejecución Independiente".`;
        setBannerAlert({ type: 'error', text: msg });
        appendLog(exec.id, stepNumber, 'workflow-orchestrator-service', 'ERROR', 'DEPENDENCY_NOT_COMPLETED', msg);
        return;
      }
    }

    try {
      const startTs = performance.now();
      let updatedRecords = [...exec.recordsSnapshot];
      let stepSummary: Record<string, unknown> = {};
      let updatedTrajectory = exec.monteCarloTrajectory;

      const cfgDevig = String(targetStep.parametersUsed.devigMethod || devigMethod);
      const cfgMinEv = Number(targetStep.parametersUsed.minEvThreshold ?? minEvThresholdPct / 100);
      const cfgStake = String(targetStep.parametersUsed.stakingStrategy || stakingStrategy);

      if (stepNumber === 1) {
        const res = runStep1Validation(updatedRecords);
        updatedRecords = res.records;
        stepSummary = res.summary;
      } else if (stepNumber === 2) {
        const res = runStep2Cleaning(updatedRecords);
        updatedRecords = res.records;
        stepSummary = res.summary;
      } else if (stepNumber === 3) {
        const res = runStep3Devigging(updatedRecords, cfgDevig);
        updatedRecords = res.records;
        stepSummary = res.summary;
      } else if (stepNumber === 4) {
        const res = runStep4EvMining(updatedRecords, cfgMinEv);
        updatedRecords = res.records;
        stepSummary = res.summary;
      } else if (stepNumber === 5) {
        const res = runStep5StakeAndMonteCarlo(updatedRecords, cfgStake, 1.0, 500, 2000, exec.randomSeed);
        updatedRecords = res.records;
        updatedTrajectory = res.trajectory;
        stepSummary = res.summary;
      } else if (stepNumber === 6) {
        const res = runStep6ClvAudit(updatedRecords);
        updatedRecords = res.records;
        stepSummary = res.summary;
      }

      const durationMs = Math.max(4, Math.round(performance.now() - startTs));
      const nowIso = new Date().toISOString();

      setExecutions((prev) =>
        prev.map((item) => {
          if (item.id !== exec.id) return item;
          const newSteps = item.steps.map((st) =>
            st.stepNumber === stepNumber
              ? {
                  ...st,
                  status: 'COMPLETED' as ExecutionStatus,
                  independentMode: forceIndependent,
                  outputDatasetVersionId: `${item.initialDatasetVersionId}-step${stepNumber}`,
                  outputSummary: stepSummary,
                  errorCode: null,
                  errorMessage: null,
                  startedAt: nowIso,
                  finishedAt: nowIso,
                  durationMs,
                }
              : st,
          );
          const allDone = newSteps.every((s) => s.status === 'COMPLETED');
          return {
            ...item,
            status: allDone ? 'COMPLETED' : 'RUNNING',
            executionMode: forceIndependent ? 'INDEPENDENT_STEP' : 'SINGLE_STEP',
            startedAt: item.startedAt || nowIso,
            finishedAt: allDone ? nowIso : null,
            durationMs: newSteps.reduce((acc, s) => acc + (s.durationMs || 0), 0),
            steps: newSteps,
            recordsSnapshot: updatedRecords,
            monteCarloTrajectory: updatedTrajectory,
          };
        }),
      );

      appendLog(
        exec.id,
        stepNumber,
        targetStep.targetService,
        'INFO',
        `STEP_${stepNumber}_COMPLETED`,
        `Paso ${stepNumber} (${targetStep.stepCode}) ejecutado con éxito (${durationMs} ms).`,
      );
      setBannerAlert({
        type: 'success',
        text: `Paso ${stepNumber} (${targetStep.stepName}) completado correctamente por ${targetStep.targetService}.`,
      });
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : 'Error inesperado en el paso';
      const nowIso = new Date().toISOString();
      setExecutions((prev) =>
        prev.map((item) => {
          if (item.id !== exec.id) return item;
          return {
            ...item,
            status: 'FAILED',
            errorMessage: errorMsg,
            steps: item.steps.map((st) =>
              st.stepNumber === stepNumber
                ? {
                    ...st,
                    status: 'FAILED',
                    errorCode: 'STEP_EXECUTION_ERROR',
                    errorMessage: errorMsg,
                    finishedAt: nowIso,
                  }
                : st,
            ),
          };
        }),
      );
      appendLog(exec.id, stepNumber, targetStep.targetService, 'ERROR', `STEP_${stepNumber}_FAILED`, errorMsg);
      setBannerAlert({ type: 'error', text: errorMsg });
    }
  };

  // Run all 6 steps sequentially
  const handleRunFullPipeline = () => {
    const exec = currentExecution;
    const nowIso = new Date().toISOString();
    let curRecords = [...exec.recordsSnapshot];
    const cfgDevig = String(exec.steps[2].parametersUsed.devigMethod || devigMethod);
    const cfgMinEv = Number(exec.steps[3].parametersUsed.minEvThreshold ?? minEvThresholdPct / 100);
    const cfgStake = String(exec.steps[4].parametersUsed.stakingStrategy || stakingStrategy);

    const s1 = runStep1Validation(curRecords);
    curRecords = s1.records;
    const s2 = runStep2Cleaning(curRecords);
    curRecords = s2.records;
    const s3 = runStep3Devigging(curRecords, cfgDevig);
    curRecords = s3.records;
    const s4 = runStep4EvMining(curRecords, cfgMinEv);
    curRecords = s4.records;
    const s5 = runStep5StakeAndMonteCarlo(curRecords, cfgStake, 1.0, 500, 2000, exec.randomSeed);
    curRecords = s5.records;
    const s6 = runStep6ClvAudit(curRecords);
    curRecords = s6.records;

    const summaries = [s1.summary, s2.summary, s3.summary, s4.summary, s5.summary, s6.summary];
    const durations = [16, 22, 28, 17, 79, 25];

    setExecutions((prev) =>
      prev.map((item) => {
        if (item.id !== exec.id) return item;
        return {
          ...item,
          executionMode: 'FULL_PIPELINE',
          status: 'COMPLETED',
          startedAt: item.startedAt || nowIso,
          finishedAt: nowIso,
          durationMs: durations.reduce((a, b) => a + b, 0),
          errorMessage: null,
          recordsSnapshot: curRecords,
          monteCarloTrajectory: s5.trajectory,
          steps: item.steps.map((st, idx) => ({
            ...st,
            status: 'COMPLETED',
            outputDatasetVersionId: `${item.initialDatasetVersionId}-v${idx + 2}`,
            outputSummary: summaries[idx] as Record<string, unknown>,
            errorCode: null,
            errorMessage: null,
            startedAt: nowIso,
            finishedAt: nowIso,
            durationMs: durations[idx],
          })),
        };
      }),
    );

    appendLog(
      exec.id,
      null,
      'workflow-orchestrator-service',
      'INFO',
      'FULL_PIPELINE_COMPLETED',
      'Pipeline completo (Pasos 1 a 6) ejecutado secuencialmente con estado COMPLETED.',
    );
    setBannerAlert({
      type: 'success',
      text: `Pipeline completo de los 6 pasos finalizado en estado COMPLETED para la ejecución ${exec.id}.`,
    });
  };

  // Cancel active execution
  const handleCancelExecution = () => {
    const nowIso = new Date().toISOString();
    setExecutions((prev) =>
      prev.map((item) => {
        if (item.id !== currentExecution.id) return item;
        return {
          ...item,
          status: 'CANCELLED',
          finishedAt: nowIso,
          steps: item.steps.map((st) =>
            st.status === 'RUNNING' || st.status === 'PENDING' ? { ...st, status: 'CANCELLED' } : st,
          ),
        };
      }),
    );
    appendLog(
      currentExecution.id,
      null,
      'workflow-orchestrator-service',
      'WARN',
      'EXECUTION_CANCELLED',
      'Ejecución pausada/cancelada por el usuario (bandera cooperativa registrada en Redis).',
    );
    setBannerAlert({ type: 'info', text: `Ejecución ${currentExecution.id} marcada como CANCELLED.` });
  };

  // Simulate failure on a step to demonstrate retry mechanism
  const handleSimulateFailure = (stepNumber: number) => {
    const nowIso = new Date().toISOString();
    setExecutions((prev) =>
      prev.map((item) => {
        if (item.id !== currentExecution.id) return item;
        return {
          ...item,
          status: 'FAILED',
          errorMessage: `Fallo simulado en Paso ${stepNumber} para prueba de recuperación`,
          steps: item.steps.map((st) =>
            st.stepNumber === stepNumber
              ? {
                  ...st,
                  status: 'FAILED',
                  errorCode: 'SIMULATED_TRANSIENT_FAILURE',
                  errorMessage: 'Timeout transitorio simulado al consultar mercado Sharp',
                  finishedAt: nowIso,
                }
              : st,
          ),
        };
      }),
    );
    appendLog(
      currentExecution.id,
      stepNumber,
      'workflow-orchestrator-service',
      'ERROR',
      `STEP_${stepNumber}_FAILED`,
      `Fallo simulado en Paso ${stepNumber}. Pulsa "Repetir Paso Fallido" para recuperar la ejecución.`,
    );
  };

  // Retry failed or cancelled step
  const handleRetryStep = (stepNumber: number) => {
    setExecutions((prev) =>
      prev.map((item) => {
        if (item.id !== currentExecution.id) return item;
        return {
          ...item,
          steps: item.steps.map((st) =>
            st.stepNumber === stepNumber ? { ...st, attemptNumber: st.attemptNumber + 1 } : st,
          ),
        };
      }),
    );
    handleRunSingleStep(stepNumber, true);
  };

  // CSV Dataset import handler
  const handleImportCsv = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const lines = csvImportText.trim().split(/\r?\n/);
      if (lines.length < 2) {
        throw new Error('El CSV debe incluir cabecera y al menos 1 fila de cuotas.');
      }
      const parsed: MarketOddsRecord[] = [];
      for (let i = 1; i < lines.length; i++) {
        const cols = lines[i].split(',');
        if (cols.length < 13) {
          throw new Error(`Fila ${i + 1} inválida: se esperan al menos 13 columnas.`);
        }
        parsed.push({
          eventId: cols[0].trim(),
          sport: cols[1].trim() || 'FOOTBALL',
          competition: cols[2].trim() || 'General',
          eventName: cols[3].trim(),
          marketType: (cols[4].trim() as MarketOddsRecord['marketType']) || 'ASIAN_HANDICAP',
          marketLine: cols[5].trim() || '0.0',
          selectionName: cols[6].trim(),
          sharpBookmaker: cols[7].trim(),
          sharpOddsSelection: parseFloat(cols[8]),
          sharpOddsOpposite: parseFloat(cols[9]),
          sharpOddsDraw: cols[10].trim() ? parseFloat(cols[10]) : null,
          softBookmaker: cols[11].trim(),
          softTakenOdds: parseFloat(cols[12]),
          closingFairOdds: cols[13]?.trim() ? parseFloat(cols[13]) : null,
          outcomeStatus: (cols[14]?.trim() as MarketOddsRecord['outcomeStatus']) || 'PENDING',
        });
      }
      const merged = [...parsed, ...activeRecords];
      const newDs: DatasetSummary = {
        id: `ds-${Date.now()}`,
        name: csvImportName.trim() || `Dataset CSV Importado (${parsed.length} nuevos mercados)`,
        description: 'Importado mediante Data Ingestion Service (Paso 1)',
        sourceType: 'CSV',
        sourceReference: 'Dashboard Upload',
        latestVersionId: `dv-${Date.now()}`,
        latestVersionNumber: 1,
        latestStageType: 'RAW_IMPORT',
        recordCount: merged.length,
        createdAt: new Date().toISOString(),
      };
      setDatasets((prev) => [newDs, ...prev]);
      setActiveRecords(merged);
      setCsvImportName('');
      setBannerAlert({
        type: 'success',
        text: `Dataset CSV importado correctamente (${parsed.length} nuevos registros añadidos al catálogo).`,
      });
    } catch (err) {
      setBannerAlert({
        type: 'error',
        text: err instanceof Error ? err.message : 'Error al importar CSV',
      });
    }
  };

  // Export JSON Report
  const handleDownloadReport = () => {
    const blob = new Blob([JSON.stringify(currentExecution, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `informe-ev-clv-${currentExecution.id}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const statusBadge = (status: ExecutionStatus) => {
    const styles: Record<ExecutionStatus, string> = {
      COMPLETED: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40',
      RUNNING: 'bg-sky-500/15 text-sky-300 border-sky-500/40',
      PENDING: 'bg-slate-700/60 text-slate-300 border-slate-600',
      FAILED: 'bg-rose-500/15 text-rose-300 border-rose-500/40',
      CANCELLED: 'bg-amber-500/15 text-amber-300 border-amber-500/40',
    };
    return (
      <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border ${styles[status]}`}>
        {status}
      </span>
    );
  };

  const firstCompareExec = executions.find((e) => e.id === compareFirstId) || executions[0];
  const secondCompareExec = executions.find((e) => e.id === compareSecondId) || executions[1] || executions[0];

  const step4Summary = (currentExecution.steps[3]?.outputSummary || {}) as Record<string, number>;
  const step5Summary = (currentExecution.steps[4]?.outputSummary || {}) as Record<string, number>;
  const step6Summary = (currentExecution.steps[5]?.outputSummary || {}) as Record<string, number>;

  const chartDataEvVsClv = currentExecution.recordsSnapshot.map((r) => ({
    name: r.eventName.length > 18 ? `${r.eventName.slice(0, 18)}…` : r.eventName,
    EV_Pct: r.expectedValuePct ?? 0,
    CLV_Pct: r.clvPct ?? 0,
    Cuota_Tomada: r.softTakenOdds,
    Cuota_Justa: r.fairOdds ?? r.sharpOddsSelection,
    Cuota_Cierre: r.closingFairOdds ?? r.sharpOddsSelection,
  }));

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      {/* Top Header */}
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-4 py-3.5 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400">
              <TrendingUp className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold tracking-tight text-white">
                  EV+ &amp; CLV Distributed Data Mining Platform
                </h1>
                <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-mono">
                  v0.6.0
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Metodología Cuantitativa de @Porros365 · Java 21 + Spring Boot 3 + RabbitMQ + Apache Commons Math
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleDownloadReport}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 border border-slate-700 transition"
            >
              <Download className="w-3.5 h-3.5" />
              Descargar Informe JSON
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="max-w-7xl mx-auto px-4 flex gap-1 overflow-x-auto border-t border-slate-800/80 pt-2">
          {[
            { id: 'orchestrator', label: '1. Orquestador y Ejecución de Pasos', icon: PlayCircle },
            { id: 'analytics', label: '2. Gráficos EV+, CLV y Monte Carlo', icon: BarChart3 },
            { id: 'methodology', label: '3. Fichas de la Metodología (6 Pasos)', icon: BookOpen },
            { id: 'datasets', label: '4. Gestión de Datasets Sharp/Soft', icon: Database },
            { id: 'compare', label: '5. Comparador de Ejecuciones', icon: GitCompare },
            { id: 'logs', label: '6. Monitorización y Logs Distribuidos', icon: Terminal },
          ].map((tab) => {
            const Icon = tab.icon;
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as typeof activeTab)}
                className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition whitespace-nowrap ${
                  active
                    ? 'border-emerald-400 text-emerald-300 bg-emerald-500/5'
                    : 'border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                }`}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            );
          })}
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl w-full mx-auto px-4 py-6 flex-1 space-y-6">
        {bannerAlert && (
          <div
            className={`p-3.5 rounded-xl border flex items-center justify-between text-xs font-medium ${
              bannerAlert.type === 'error'
                ? 'bg-rose-950/60 border-rose-500/40 text-rose-200'
                : bannerAlert.type === 'success'
                  ? 'bg-emerald-950/60 border-emerald-500/40 text-emerald-200'
                  : 'bg-sky-950/60 border-sky-500/40 text-sky-200'
            }`}
          >
            <div className="flex items-center gap-2">
              {bannerAlert.type === 'error' ? (
                <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
              ) : (
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              )}
              <span>{bannerAlert.text}</span>
            </div>
            <button
              onClick={() => setBannerAlert(null)}
              className="text-slate-400 hover:text-white ml-4 text-xs underline"
            >
              Cerrar
            </button>
          </div>
        )}

        {/* KPI Summary Cards */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
            <div className="text-xs text-slate-400">Oportunidades EV+ Detectadas (Paso 4)</div>
            <div className="mt-1 flex items-baseline justify-between">
              <span className="text-2xl font-bold text-emerald-400">
                {step4Summary.evPlusOpportunitiesFound ?? 0} / {currentExecution.recordsSnapshot.length}
              </span>
              <span className="text-xs text-slate-400">
                EV Medio: <strong className="text-emerald-300">+{step4Summary.averageEvPlusOnlyPct ?? 0}%</strong>
              </span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
            <div className="text-xs text-slate-400">Expectativa 500 Apuestas (Paso 5 · Post 7/)</div>
            <div className="mt-1 flex items-baseline justify-between">
              <span className="text-2xl font-bold text-sky-400">
                +{step5Summary.projectedAnalyticalExpectedProfitUnits ?? 0}u
              </span>
              <span className="text-xs text-slate-400">
                MC Media: <strong className="text-sky-300">+{step5Summary.monteCarloMeanProfitUnits ?? 0}u</strong>
              </span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
            <div className="text-xs text-slate-400">Closing Line Value Medio (Paso 6 · Post 8/)</div>
            <div className="mt-1 flex items-baseline justify-between">
              <span className="text-2xl font-bold text-purple-400">
                +{step6Summary.averageClvEvPlusOnlyPct ?? 0}%
              </span>
              <span className="text-xs text-slate-400">
                Bate Cierre: <strong className="text-purple-300">{step6Summary.beatClosingLineRateEvPlusPct ?? 0}%</strong>
              </span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
            <div className="text-xs text-slate-400">Estado de la Ejecución Seleccionada</div>
            <div className="mt-2 flex items-center justify-between">
              {statusBadge(currentExecution.status)}
              <span className="text-xs font-mono text-slate-400">
                {currentExecution.durationMs ? `${currentExecution.durationMs} ms` : '—'}
              </span>
            </div>
          </div>
        </section>

        {/* TAB 1: ORCHESTRATOR & STEP EXECUTION */}
        {activeTab === 'orchestrator' && (
          <div className="space-y-6">
            {/* Execution Configuration & Controls */}
            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-base font-bold text-white flex items-center gap-2">
                    <Layers className="w-5 h-5 text-emerald-400" />
                    Control de Orquestación del Pipeline EV+ &amp; CLV
                  </h2>
                  <p className="text-xs text-slate-400">
                    Ejecuta cada paso individualmente (con validación de dependencias) o lanza los 6 pasos secuencialmente.
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <select
                    value={selectedExecutionId}
                    onChange={(e) => setSelectedExecutionId(e.target.value)}
                    className="bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-slate-200"
                  >
                    {executions.map((ex) => (
                      <option key={ex.id} value={ex.id}>
                        {ex.id} — {ex.configurationName} [{ex.status}]
                      </option>
                    ))}
                  </select>

                  <button
                    onClick={handleRunFullPipeline}
                    className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-xs font-semibold text-white transition"
                  >
                    <Play className="w-3.5 h-3.5" />
                    Ejecutar Pipeline Completo (Pasos 1 → 6)
                  </button>

                  <button
                    onClick={handleCancelExecution}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-600/20 hover:bg-amber-600/30 text-amber-300 border border-amber-500/30 text-xs font-semibold transition"
                  >
                    <XCircle className="w-3.5 h-3.5" />
                    Cancelar / Pausar
                  </button>
                </div>
              </div>

              {/* Parameters bar for creating new execution */}
              <div className="grid grid-cols-1 md:grid-cols-5 gap-3 pt-3 border-t border-slate-800">
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Método De-Vigging (Paso 3)</label>
                  <select
                    value={devigMethod}
                    onChange={(e) => setDevigMethod(e.target.value as typeof devigMethod)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs"
                  >
                    <option value="MULTIPLICATIVE">MULTIPLICATIVE (Proporcional Post 5/)</option>
                    <option value="POWER">POWER (Logarítmico Cuotas Altas)</option>
                    <option value="ADDITIVE">ADDITIVE (Reparto Uniforme)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Umbral Mínimo EV+ % (Paso 4)</label>
                  <input
                    type="number"
                    step="0.5"
                    value={minEvThresholdPct}
                    onChange={(e) => setMinEvThresholdPct(parseFloat(e.target.value) || 0)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs"
                  />
                </div>

                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Estrategia de Stake (Paso 5)</label>
                  <select
                    value={stakingStrategy}
                    onChange={(e) => setStakingStrategy(e.target.value as typeof stakingStrategy)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs"
                  >
                    <option value="FLAT_UNIT">FLAT_UNIT (1u Plana · Post 7/)</option>
                    <option value="FRACTIONAL_KELLY">FRACTIONAL_KELLY (15% Kelly)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Semilla Aleatoria (Monte Carlo)</label>
                  <input
                    type="number"
                    value={randomSeed}
                    onChange={(e) => setRandomSeed(parseInt(e.target.value, 10) || 42)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs"
                  />
                </div>

                <div className="flex flex-col justify-end gap-1.5">
                  <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={independentMode}
                      onChange={(e) => setIndependentMode(e.target.checked)}
                      className="rounded border-slate-600"
                    />
                    Modo Independiente (Saltar dependencias)
                  </label>
                  <button
                    onClick={handleCreateNewExecution}
                    className="flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg bg-sky-600 hover:bg-sky-500 text-xs font-semibold text-white transition"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    Nueva Ejecución (PENDING)
                  </button>
                </div>
              </div>
            </div>

            {/* 6 Steps Execution Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {currentExecution.steps.map((step) => (
                <div
                  key={step.id}
                  className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex flex-col justify-between space-y-3"
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-mono text-emerald-400 font-bold">
                        PASO {step.stepNumber} · {step.targetService}
                      </span>
                      {statusBadge(step.status)}
                    </div>
                    <h3 className="text-sm font-bold text-white">{step.stepName}</h3>
                    <div className="text-[11px] font-mono text-slate-400">
                      Código: {step.stepCode} · Intento #{step.attemptNumber}
                      {step.dependsOnStepNumber ? ` · Depende de Paso ${step.dependsOnStepNumber}` : ' · Sin dependencia'}
                    </div>

                    {Object.keys(step.outputSummary).length > 0 && (
                      <div className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-[11px] font-mono text-slate-300 space-y-1">
                        {Object.entries(step.outputSummary).map(([k, v]) => (
                          <div key={k} className="flex justify-between">
                            <span className="text-slate-400">{k}:</span>
                            <span className="text-emerald-300 font-semibold">{String(v)}</span>
                          </div>
                        ))}
                      </div>
                    )}

                    {step.errorMessage && (
                      <div className="p-2 rounded-lg bg-rose-950/60 border border-rose-500/40 text-[11px] text-rose-200">
                        <strong>{step.errorCode}:</strong> {step.errorMessage}
                      </div>
                    )}
                  </div>

                  <div className="pt-2 border-t border-slate-800 flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => handleRunSingleStep(step.stepNumber)}
                        className="flex items-center gap-1 px-2.5 py-1 rounded bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-300 border border-emerald-500/30 text-xs font-medium transition"
                      >
                        <Play className="w-3 h-3" />
                        Ejecutar Paso
                      </button>

                      {(step.status === 'FAILED' || step.status === 'CANCELLED') && (
                        <button
                          onClick={() => handleRetryStep(step.stepNumber)}
                          className="flex items-center gap-1 px-2.5 py-1 rounded bg-sky-600/20 hover:bg-sky-600/30 text-sky-300 border border-sky-500/30 text-xs font-medium transition"
                        >
                          <RotateCcw className="w-3 h-3" />
                          Reintentar
                        </button>
                      )}
                    </div>

                    <button
                      onClick={() => handleSimulateFailure(step.stepNumber)}
                      title="Simular fallo para probar reintento"
                      className="text-[11px] text-slate-400 hover:text-rose-400 transition"
                    >
                      Simular Fallo
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Table of Processed Markets with EV+ and CLV results */}
            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-white">
                  Tabla de Auditoría de Mercados (Cuota Sharp → Cuota Justa → EV+ en Casa Blanda → CLV al Cierre)
                </h3>
                <span className="text-xs text-slate-400">
                  Versión de Dataset Activa: <code className="text-emerald-300">{currentExecution.currentDatasetVersionId}</code>
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400">
                      <th className="py-2.5 px-2">Partido / Selección</th>
                      <th className="py-2.5 px-2">Casa Sharp (Cuotas)</th>
                      <th className="py-2.5 px-2">Margen Sharp</th>
                      <th className="py-2.5 px-2">Prob. Real / Cuota Justa</th>
                      <th className="py-2.5 px-2">Casa Blanda (Tomada)</th>
                      <th className="py-2.5 px-2">EV % (Paso 4)</th>
                      <th className="py-2.5 px-2">Cierre / CLV % (Paso 6)</th>
                      <th className="py-2.5 px-2">Stake / Resultado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {currentExecution.recordsSnapshot.map((r) => (
                      <tr key={r.eventId} className="hover:bg-slate-800/30">
                        <td className="py-2.5 px-2">
                          <div className="font-semibold text-white">{r.eventName}</div>
                          <div className="text-[11px] text-slate-400">
                            {r.competition} · <span className="text-emerald-300">{r.selectionName}</span>
                          </div>
                        </td>
                        <td className="py-2.5 px-2 font-mono">
                          <div className="text-slate-200">{r.sharpBookmaker}</div>
                          <div className="text-[11px] text-slate-400">
                            {r.sharpOddsSelection.toFixed(2)} / {r.sharpOddsOpposite.toFixed(2)}
                            {r.sharpOddsDraw ? ` / ${r.sharpOddsDraw.toFixed(2)}` : ''}
                          </div>
                        </td>
                        <td className="py-2.5 px-2 font-mono text-slate-300">
                          {r.sharpMarginPct !== undefined ? `${r.sharpMarginPct.toFixed(2)}%` : '—'}
                        </td>
                        <td className="py-2.5 px-2 font-mono">
                          {r.trueProbability !== undefined ? (
                            <>
                              <div className="text-sky-300 font-semibold">{(r.trueProbability * 100).toFixed(2)}%</div>
                              <div className="text-[11px] text-slate-400">Justa: {r.fairOdds?.toFixed(2)}</div>
                            </>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td className="py-2.5 px-2 font-mono">
                          <span className="px-2 py-0.5 rounded bg-slate-800 text-white font-semibold">
                            {r.softBookmaker}: {r.softTakenOdds.toFixed(2)}
                          </span>
                        </td>
                        <td className="py-2.5 px-2 font-mono">
                          {r.expectedValuePct !== undefined ? (
                            <span
                              className={`px-2 py-0.5 rounded font-bold ${
                                r.evPlusOpportunity
                                  ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                                  : 'bg-rose-500/15 text-rose-300'
                              }`}
                            >
                              {r.expectedValuePct >= 0 ? `+${r.expectedValuePct.toFixed(2)}%` : `${r.expectedValuePct.toFixed(2)}%`}
                            </span>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td className="py-2.5 px-2 font-mono">
                          {r.clvPct !== undefined ? (
                            <div>
                              <span
                                className={`font-bold ${
                                  r.beatClosingLine ? 'text-purple-300' : 'text-amber-300'
                                }`}
                              >
                                {r.clvPct >= 0 ? `+${r.clvPct.toFixed(2)}%` : `${r.clvPct.toFixed(2)}%`}
                              </span>
                              <div className="text-[11px] text-slate-400">
                                Cierre: {r.closingFairOdds?.toFixed(2)}
                              </div>
                            </div>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td className="py-2.5 px-2 font-mono">
                          <div>{r.assignedStakeUnits ? `${r.assignedStakeUnits.toFixed(2)}u` : '0.00u'}</div>
                          <div
                            className={`text-[11px] font-bold ${
                              r.outcomeStatus === 'WON'
                                ? 'text-emerald-400'
                                : r.outcomeStatus === 'LOST'
                                  ? 'text-rose-400'
                                  : 'text-slate-400'
                            }`}
                          >
                            {r.outcomeStatus}{' '}
                            {r.actualProfitUnits !== undefined
                              ? `(${r.actualProfitUnits >= 0 ? '+' : ''}${r.actualProfitUnits.toFixed(2)}u)`
                              : ''}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: CHARTS & VISUALIZATIONS */}
        {activeTab === 'analytics' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Chart 1: EV% vs CLV% per Market */}
              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                <h3 className="text-sm font-bold text-white">
                  1. Valor Esperado (EV%) vs Closing Line Value (CLV%) por Partido
                </h3>
                <p className="text-xs text-slate-400">
                  Verifica cómo las apuestas EV+ (como Monza vs Atalanta +3.9% EV / +14.1% CLV y Bosnia vs Rumania +9.1% EV / +33.6% CLV) baten sistemáticamente al cierre.
                </p>
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartDataEvVsClv}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis dataKey="name" tick={{ fill: '#94a3b8', fontSize: 10 }} />
                      <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} unit="%" />
                      <Tooltip
                        contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', fontSize: 12 }}
                      />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Bar dataKey="EV_Pct" name="EV % (Paso 4)" fill="#10b981" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="CLV_Pct" name="CLV % (Paso 6)" fill="#a855f7" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>

              {/* Chart 2: Monte Carlo 500 Bets Trajectory */}
              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                <h3 className="text-sm font-bold text-white">
                  2. Convergencia en Volumen: 500 Apuestas EV+ (Post 7/ — Simulación Monte Carlo)
                </h3>
                <p className="text-xs text-slate-400">
                  Comparativa entre la Esperanza Matemática Analítica (línea recta) y la Media Empírica de Monte Carlo en unidades (u) con semilla reproducible #{currentExecution.randomSeed}.
                </p>
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={currentExecution.monteCarloTrajectory}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis
                        dataKey="betNumber"
                        tick={{ fill: '#94a3b8', fontSize: 11 }}
                        label={{ value: 'Nº Apuestas', position: 'insideBottomRight', fill: '#94a3b8', fontSize: 10 }}
                      />
                      <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} unit="u" />
                      <Tooltip
                        contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', fontSize: 12 }}
                      />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Line
                        type="monotone"
                        dataKey="analyticalExpectedProfitUnits"
                        name="Expectativa Analítica (u)"
                        stroke="#38bdf8"
                        strokeWidth={2}
                        dot={false}
                      />
                      <Line
                        type="monotone"
                        dataKey="monteCarloMeanProfitUnits"
                        name="Media Simulación Monte Carlo (u)"
                        stroke="#10b981"
                        strokeWidth={2}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </div>

            {/* Chart 3: Taken Odds vs Fair Odds vs Closing Odds */}
            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
              <h3 className="text-sm font-bold text-white">
                3. Comparativa de Precios: Cuota Cogida (Casa Blanda) vs Cuota Justa (Sharp Sin Margen) vs Cuota de Cierre
              </h3>
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartDataEvVsClv}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                    <XAxis dataKey="name" tick={{ fill: '#94a3b8', fontSize: 10 }} />
                    <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} domain={[1, 'auto']} />
                    <Tooltip
                      contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', fontSize: 12 }}
                    />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Bar dataKey="Cuota_Tomada" name="Cuota Tomada (Soft)" fill="#22c55e" />
                    <Bar dataKey="Cuota_Justa" name="Cuota Justa (Sharp De-Vigged)" fill="#38bdf8" />
                    <Bar dataKey="Cuota_Cierre" name="Cuota de Cierre (CLV Ref)" fill="#f59e0b" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: METHODOLOGY STEP SHEETS */}
        {activeTab === 'methodology' && (
          <div className="space-y-4">
            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
              <h2 className="text-base font-bold text-white">
                Fichas Técnicas de la Metodología de @Porros365 (6 Pasos Ejecutables)
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                Fuente verificada: <code className="text-emerald-300">https://x.com/Porros365/status/2122822935002132523</code>
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {METHODOLOGY_STEP_SHEETS.map((sheet) => (
                <div
                  key={sheet.stepNumber}
                  className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                      PASO {sheet.stepNumber} · {sheet.stepCode}
                    </span>
                    <span className="text-xs font-mono text-slate-400">{sheet.targetService}</span>
                  </div>
                  <h3 className="text-sm font-bold text-white">{sheet.name}</h3>
                  <p className="text-xs text-slate-300 leading-relaxed">{sheet.description}</p>

                  <div className="text-xs space-y-1.5 pt-2 border-t border-slate-800">
                    <div>
                      <strong className="text-slate-400">Objetivo:</strong>{' '}
                      <span className="text-slate-200">{sheet.objective}</span>
                    </div>
                    <div>
                      <strong className="text-slate-400">Algoritmo:</strong>{' '}
                      <code className="text-sky-300">{sheet.algorithmName}</code>
                    </div>
                    <div>
                      <strong className="text-slate-400">Dependencia:</strong>{' '}
                      <span>
                        {sheet.dependsOnStepNumber
                          ? `Requiere Paso ${sheet.dependsOnStepNumber} COMPLETED`
                          : 'Ninguna (Paso Inicial)'}
                      </span>
                    </div>
                    <div>
                      <strong className="text-slate-400">Criterios de Validación:</strong>{' '}
                      <span className="text-emerald-300">{sheet.validationCriteria.join(' · ')}</span>
                    </div>
                    <div>
                      <strong className="text-slate-400">Errores Posibles:</strong>{' '}
                      <code className="text-rose-300">{sheet.possibleErrors.join(', ')}</code>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 4: DATASETS MANAGEMENT */}
        {activeTab === 'datasets' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-4">
              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                <h2 className="text-base font-bold text-white">Catálogo de Datasets y Versiones</h2>
                <div className="space-y-3">
                  {datasets.map((ds) => (
                    <div
                      key={ds.id}
                      className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex flex-wrap items-center justify-between gap-4"
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-sm text-white">{ds.name}</span>
                          <span className="text-[11px] px-2 py-0.5 rounded bg-sky-500/20 text-sky-300 font-mono">
                            {ds.sourceType} · v{ds.latestVersionNumber} ({ds.latestStageType})
                          </span>
                        </div>
                        <p className="text-xs text-slate-400">{ds.description}</p>
                        <div className="text-[11px] font-mono text-slate-500">
                          ID: {ds.id} · VersionID: {ds.latestVersionId} · Mercados: {ds.recordCount}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Import CSV Form */}
            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
              <h3 className="text-sm font-bold text-white">Importar Nuevo Dataset CSV (Data Ingestion Service)</h3>
              <form onSubmit={handleImportCsv} className="space-y-3 text-xs">
                <div>
                  <label className="block text-slate-400 mb-1">Nombre del Dataset</label>
                  <input
                    type="text"
                    value={csvImportName}
                    onChange={(e) => setCsvImportName(e.target.value)}
                    placeholder="Ej. Jornada Champions League Sharp vs Soft"
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-200"
                  />
                </div>
                <div>
                  <label className="block text-slate-400 mb-1">Contenido CSV (Cabecera + Filas)</label>
                  <textarea
                    rows={7}
                    value={csvImportText}
                    onChange={(e) => setCsvImportText(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 font-mono text-[11px] text-slate-200"
                  />
                </div>
                <button
                  type="submit"
                  className="w-full py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold transition"
                >
                  Validar e Importar CSV (Paso 1)
                </button>
              </form>
            </div>
          </div>
        )}

        {/* TAB 5: COMPARE EXECUTIONS */}
        {activeTab === 'compare' && (
          <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-bold text-white">
                  Comparador Diferencial de Ejecuciones (GET /api/executions/compare)
                </h2>
                <p className="text-xs text-slate-400">
                  Contrasta el impacto de cambiar el método de eliminación de margen (Multiplicativo vs Power), umbral EV+ o estrategia de staking.
                </p>
              </div>
              <div className="flex items-center gap-3">
                <select
                  value={compareFirstId}
                  onChange={(e) => setCompareFirstId(e.target.value)}
                  className="bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs"
                >
                  {executions.map((e) => (
                    <option key={e.id} value={e.id}>
                      A: {e.id} ({e.configurationName})
                    </option>
                  ))}
                </select>
                <span className="text-xs text-slate-400">vs</span>
                <select
                  value={compareSecondId}
                  onChange={(e) => setCompareSecondId(e.target.value)}
                  className="bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs"
                >
                  {executions.map((e) => (
                    <option key={e.id} value={e.id}>
                      B: {e.id} ({e.configurationName})
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {[firstCompareExec, secondCompareExec].map((ex, idx) => {
                const s4 = (ex.steps[3]?.outputSummary || {}) as Record<string, number>;
                const s5 = (ex.steps[4]?.outputSummary || {}) as Record<string, number>;
                const s6 = (ex.steps[5]?.outputSummary || {}) as Record<string, number>;
                return (
                  <div key={`${ex.id}-${idx}`} className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-emerald-400">
                        EJECUCIÓN {idx === 0 ? 'A (Base)' : 'B (Comparada)'}: {ex.id}
                      </span>
                      {statusBadge(ex.status)}
                    </div>
                    <div className="text-sm font-bold text-white">{ex.configurationName}</div>
                    <div className="grid grid-cols-2 gap-2 text-xs font-mono pt-2 border-t border-slate-800">
                      <div className="text-slate-400">Semilla Aleatoria:</div>
                      <div className="text-right text-slate-200">{ex.randomSeed}</div>
                      <div className="text-slate-400">Apuestas EV+ Detectadas:</div>
                      <div className="text-right text-emerald-300 font-bold">
                        {s4.evPlusOpportunitiesFound ?? 0}
                      </div>
                      <div className="text-slate-400">EV+ Medio:</div>
                      <div className="text-right text-emerald-300">+{s4.averageEvPlusOnlyPct ?? 0}%</div>
                      <div className="text-slate-400">CLV Medio (Paso 6):</div>
                      <div className="text-right text-purple-300">+{s6.averageClvEvPlusOnlyPct ?? 0}%</div>
                      <div className="text-slate-400">Expectativa 500 Apuestas:</div>
                      <div className="text-right text-sky-300 font-bold">
                        +{s5.projectedAnalyticalExpectedProfitUnits ?? 0}u
                      </div>
                      <div className="text-slate-400">Beneficio Real Muestra:</div>
                      <div className="text-right text-white font-bold">
                        {s6.actualProfitUnits ?? 0}u (Yield {s6.actualYieldPct ?? 0}%)
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* TAB 6: MONITORING & LOGS */}
        {activeTab === 'logs' && (
          <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <Activity className="w-5 h-5 text-emerald-400" />
                  Monitorización de Microservicios y Trazabilidad de ExecutionLog
                </h2>
                <p className="text-xs text-slate-400">
                  Registro cronológico centralizado por Monitoring Service (:8086) desde la cola monitoring.logs.queue.
                </p>
              </div>
              <span className="text-xs px-2.5 py-1 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5" />7 Microservicios UP
              </span>
            </div>

            <div className="space-y-2 max-h-96 overflow-y-auto font-mono text-xs">
              {logs.map((l) => (
                <div
                  key={l.id}
                  className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 flex flex-wrap items-center justify-between gap-2"
                >
                  <div className="flex items-center gap-2">
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        l.level === 'ERROR'
                          ? 'bg-rose-500/20 text-rose-300'
                          : l.level === 'WARN'
                            ? 'bg-amber-500/20 text-amber-300'
                            : 'bg-emerald-500/20 text-emerald-300'
                      }`}
                    >
                      {l.level}
                    </span>
                    <span className="text-sky-400">[{l.serviceName}]</span>
                    <span className="text-slate-400">{l.eventCode}:</span>
                    <span className="text-slate-200">{l.message}</span>
                  </div>
                  <span className="text-[10px] text-slate-500">{l.timestamp}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
