-- V2__seed_methodology_and_algorithms.sql
-- Carga inicial del workflow PORROS365_EV_CLV_V1, las 6 fichas tecnicas y los algoritmos asociados

INSERT INTO users (id, username, email, password_hash, role, active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    'admin@porros365-evmining.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi7B8s6B9R9Q/xG3.1y1c/kK4eK1s9O',
    'ROLE_ADMIN',
    TRUE
) ON CONFLICT (username) DO NOTHING;

-- 1. Algoritmos de los 6 pasos
INSERT INTO algorithms (id, code, name, category, description) VALUES
('a0000000-0000-0000-0000-000000000001', 'ALGO_ODDS_INGESTION_VALIDATOR', 'Multi-Bookmaker Odds Ingestion & Completeness Validator', 'INGESTION', 'Valida integridad estructural de cuotas Sharp y Soft y consolida mercados completos.'),
('a0000000-0000-0000-0000-000000000002', 'ALGO_MARKET_CLEANER_IQR', 'Market Deduplication & Overround Anomaly Filter (IQR)', 'PREPROCESSING', 'Elimina duplicados, gestiona cierres nulos y filtra mercados sharp con margenes anomalos.'),
('a0000000-0000-0000-0000-000000000003', 'ALGO_SHARP_DEVIG_ENGINE', 'Sharp Bookmaker De-Vigging & Fair Odds Estimator', 'PROBABILITY_ESTIMATION', 'Elimina el margen de las casas sharp (Multiplicativo, Aditivo o Power) para estimar p_real y cuota justa.'),
('a0000000-0000-0000-0000-000000000004', 'ALGO_EV_PLUS_MINER', 'Expected Value (EV+) Discrepancy Miner', 'VALUE_MINING', 'Aplica EV = (p_real * cuota_soft) - 1 contra casas blandas y clasifica oportunidades de valor positivo.'),
('a0000000-0000-0000-0000-000000000005', 'ALGO_STAKE_MONTE_CARLO', 'Stake Sizing & Monte Carlo Volume Variance Simulator', 'STOCHASTIC_SIMULATION', 'Asigna stake (Plano/Kelly), calcula expectativa en N apuestas y simula varianza, rachas de perdidas y drawdown con Apache Commons Math.'),
('a0000000-0000-0000-0000-000000000006', 'ALGO_CLV_STATISTICAL_AUDITOR', 'Closing Line Value (CLV) & Student-t Advantage Auditor', 'STATISTICAL_INFERENCE', 'Calcula CLV = (cuota_tomada / cuota_cierre) - 1, correlacion Pearson EV-CLV, regresion lineal y test t-Student.')
ON CONFLICT (code) DO NOTHING;

-- 2. Versiones de los algoritmos
INSERT INTO algorithm_versions (id, algorithm_id, version, implementation_class, code_commit_hash, supports_random_seed, parameter_schema, default_parameters) VALUES
('a1000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', '1.0.0', 'com.porros365.evmining.ingestion.service.OddsIngestionStepProcessor', 'v0.2.0', FALSE,
 '{"minAllowedOdds":"number","maxAllowedOdds":"number"}',
 '{"minAllowedOdds":1.01,"maxAllowedOdds":1000.0,"sharpBookmakers":["BETFAIR_EXCHANGE","SINGBET","SBOBET","CIRCA"]}'),
('a1000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002', '1.0.0', 'com.porros365.evmining.processing.service.MarketCleaningStepProcessor', 'v0.2.0', FALSE,
 '{"deduplicationStrategy":"string","maxSharpMarginAllowed":"number"}',
 '{"deduplicationStrategy":"KEEP_LATEST","maxSharpMarginAllowed":0.08,"minSharpMarginAllowed":-0.01,"nullClosingOddsPolicy":"ALLOW_FOR_PREMATCH"}'),
('a1000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003', '1.0.0', 'com.porros365.evmining.mining.engine.SharpDeviggingEngine', 'v0.2.0', FALSE,
 '{"devigMethod":"MULTIPLICATIVE|ADDITIVE|POWER"}',
 '{"devigMethod":"MULTIPLICATIVE","sharpAggregationMethod":"WEIGHTED_AVERAGE"}'),
('a1000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000004', '1.0.0', 'com.porros365.evmining.mining.engine.EvPlusOpportunityEngine', 'v0.2.0', FALSE,
 '{"minEvThreshold":"number","maxEvThreshold":"number"}',
 '{"minEvThreshold":0.02,"maxEvThreshold":0.35}'),
('a1000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000005', '1.0.0', 'com.porros365.evmining.mining.engine.StakeAndMonteCarloEngine', 'v0.2.0', TRUE,
 '{"stakingStrategy":"FLAT_UNIT|FRACTIONAL_KELLY","defaultUnitStake":"number","projectionVolumeBets":"integer","simulationTrials":"integer"}',
 '{"stakingStrategy":"FLAT_UNIT","defaultUnitStake":1.0,"kellyFraction":0.15,"maxStakeUnits":2.0,"projectionVolumeBets":500,"simulationTrials":5000}'),
('a1000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000006', '1.0.0', 'com.porros365.evmining.mining.engine.ClvPerformanceAuditEngine', 'v0.2.0', FALSE,
 '{"clvReferenceType":"CLOSING_FAIR_ODDS","significanceLevelAlpha":"number"}',
 '{"clvReferenceType":"CLOSING_FAIR_ODDS","significanceLevelAlpha":0.05}')
ON CONFLICT (algorithm_id, version) DO NOTHING;

-- 3. Workflow principal de la metodologia de @Porros365
INSERT INTO workflows (id, code, name, methodology_version, description, source_thread_url, active) VALUES (
    'b0000000-0000-0000-0000-000000000001',
    'PORROS365_EV_CLV_V1',
    'Metodología Cuantitativa EV+ y Auditoría CLV (@Porros365)',
    '1.0.0',
    'Pipeline de 6 pasos basado en la eliminación de margen de casas Sharp (Betfair Exchange, SingBet, Sbobet, Circa), detección de apuestas EV+ en casas blandas (Bet365, Paf), simulación de volumen/stake y validación estadística de Closing Line Value (CLV).',
    'https://x.com/Porros365/status/2122822935002132523',
    TRUE
) ON CONFLICT (code) DO NOTHING;

-- 4. Las 6 fichas tecnicas de pasos (WorkflowStep)
INSERT INTO workflow_steps (id, workflow_id, step_number, step_code, name, description, objective, target_service, default_algorithm_id, depends_on_step_number, input_contract, output_contract, default_parameters, validation_criteria, possible_errors, expected_results) VALUES
('b1000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 1, 'ODDS_INGESTION_AND_CONSOLIDATION',
 'Ingestión y Consolidación de Mercados Sharp y Soft',
 'Importa, valida e indexa cuotas de casas Sharp (Betfair Exchange, SingBet, Sbobet, Circa) y casas Soft (Bet365, Paf), verificando que todas las opciones mutuamente excluyentes del mercado estén completas.',
 'Generar una versión de dataset estructurada y validada con trazabilidad de origen.',
 'data-ingestion-service', 'a0000000-0000-0000-0000-000000000001', NULL,
 '{"requiredFields":["eventId","eventName","marketType","selectionName","sharpBookmaker","sharpOddsSelection","sharpOddsOpposite","softBookmaker","softTakenOdds"]}',
 '{"stageType":"STEP1_CONSOLIDATED","producedFields":["validatedRecords","marketCount"]}',
 '{"minAllowedOdds":1.01,"maxAllowedOdds":1000.0}',
 '["Todas las cuotas deben ser > 1.01","Mercados 1X2 requieren cuota de empate sharpOddsDraw"]',
 '["INVALID_ODDS_VALUE","INCOMPLETE_MARKET_OUTCOMES","EMPTY_DATASET"]',
 '{"summary":"DatasetVersion validado con integridad de mercados Sharp y Soft"}'),

('b1000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 2, 'MARKET_CLEANING_AND_NORMALIZATION',
 'Limpieza, Estandarización y Detección de Anomalías de Cuota',
 'Elimina registros duplicados, normaliza identificadores de mercados y filtra mercados sharp con márgenes corruptos o ilíquidos.',
 'Garantizar que la estimación de probabilidad real no se contamine con errores de captura.',
 'data-processing-service', 'a0000000-0000-0000-0000-000000000002', 1,
 '{"requiresStage":"STEP1_CONSOLIDATED"}',
 '{"stageType":"STEP2_CLEANED","producedFields":["cleanedRecords","droppedDuplicates","droppedOutliers"]}',
 '{"deduplicationStrategy":"KEEP_LATEST","maxSharpMarginAllowed":0.08,"minSharpMarginAllowed":-0.01}',
 '["0% de duplicados por clave de evento y seleccion","Overround sharp dentro del rango [-1%, +8%]"]',
 '["EMPTY_CLEANED_DATASET","CORRUPT_SHARP_MARKET"]',
 '{"summary":"Dataset limpio y libre de anomalias de margen"}'),

('b1000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001', 3, 'SHARP_DEVIGGING_AND_FAIR_ODDS',
 'Eliminación de Margen (De-Vigging) y Cálculo de Cuota Justa',
 'Calcula probabilidades implícitas brutas (1/cuota), determina el margen de la casa sharp y lo elimina (Multiplicativo, Aditivo o Power) para obtener la probabilidad real (p_real) y la cuota justa (Fair Odds = 1/p_real).',
 'Estimar la probabilidad real sin comisión de cada selección tomando como referencia el mercado sharp.',
 'data-mining-service', 'a0000000-0000-0000-0000-000000000003', 2,
 '{"requiresStage":"STEP2_CLEANED"}',
 '{"stageType":"STEP3_DEVIGGED","producedFields":["impliedProbabilityRaw","sharpMarginPct","trueProbability","fairOdds"]}',
 '{"devigMethod":"MULTIPLICATIVE"}',
 '["Suma de probabilidades reales de todas las ramas del mercado = 1.0 +/- 1e-6","Cuota justa > cuota sharp bruta cuando el margen es positivo"]',
 '["PROBABILITY_SUM_INVARIANT_VIOLATION","NON_CONVERGENCE_POWER_DEVIG"]',
 '{"summary":"Probabilidad real p_real y cuota justa calculadas para cada mercado"}'),

('b1000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000001', 4, 'EV_PLUS_OPPORTUNITY_MINING',
 'Detección y Clasificación de Apuestas con Valor Esperado Positivo (EV+)',
 'Compara la probabilidad real sharp contra la cuota ofrecida por la casa blanda aplicando EV = (p_real * cuota_soft) - 1 y filtra las oportunidades que superan el umbral mínimo de valor esperado.',
 'Identificar oportunidades de apuesta con esperanza matemática positiva a largo plazo.',
 'data-mining-service', 'a0000000-0000-0000-0000-000000000004', 3,
 '{"requiresStage":"STEP3_DEVIGGED"}',
 '{"stageType":"STEP4_EV_MINED","producedFields":["expectedValue","expectedValuePct","evPlusOpportunity"]}',
 '{"minEvThreshold":0.02,"maxEvThreshold":0.35}',
 '["Identidad exacta EV = (trueProbability * softTakenOdds) - 1"]',
 '["NO_EV_PLUS_OPPORTUNITIES_FOUND","SOFT_ODDS_MISSING"]',
 '{"summary":"Catalogo clasificado de apuestas EV+ con ventaja matematica cuantificada"}'),

('b1000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000001', 5, 'STAKE_AND_VOLUME_SIMULATION',
 'Dimensionamiento de Stake, Expectativa a Largo Plazo y Simulación Monte Carlo',
 'Asigna el stake en unidades (1u plana o Kelly fraccional), calcula el beneficio esperado acumulado (ej. 500 apuestas a +5% = +25u) y ejecuta simulación de Monte Carlo con semilla reproducible para medir varianza, rachas de 5-6 pérdidas seguidas y drawdown.',
 'Cuantificar el rendimiento esperado con volumen y el riesgo de varianza a corto plazo.',
 'data-mining-service', 'a0000000-0000-0000-0000-000000000005', 4,
 '{"requiresStage":"STEP4_EV_MINED"}',
 '{"stageType":"STEP5_STAKED","producedFields":["assignedStakeUnits","expectedProfitUnits","monteCarloDistribution"]}',
 '{"stakingStrategy":"FLAT_UNIT","defaultUnitStake":1.0,"projectionVolumeBets":500,"simulationTrials":5000}',
 '["Convergencia de la media Monte Carlo a la esperanza matematica analitica"]',
 '["INVALID_STAKE_PARAMETERS","SIMULATION_TIMEOUT"]',
 '{"summary":"Plan de staking, expectativa de unidades (+25u en 500 apuestas al 5%) y metricas de riesgo"}'),

('b1000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000001', 6, 'CLV_AND_PERFORMANCE_AUDIT',
 'Auditoría de CLV (Closing Line Value) y Evaluación Estadística de Ventaja',
 'Compara la cuota tomada con la cuota justa de cierre: CLV = (cuota_tomada / cuota_cierre) - 1. Calcula porcentaje de apuestas que baten el cierre, correlación EV vs CLV, beneficio real vs esperado y test t-Student de ventaja sistemática.',
 'Validar científicamente si se bate al mercado de cierre de forma sistemática sin esperar a 1.000 apuestas.',
 'data-mining-service', 'a0000000-0000-0000-0000-000000000006', 5,
 '{"requiresStage":"STEP5_STAKED"}',
 '{"stageType":"STEP6_CLV_AUDITED","producedFields":["clvValue","clvPct","beatClosingLine","actualProfitUnits","tTestPValue"]}',
 '{"clvReferenceType":"CLOSING_FAIR_ODDS","significanceLevelAlpha":0.05}',
 '["Identidad exacta CLV = (softTakenOdds / closingFairOdds) - 1"]',
 '["MISSING_CLOSING_ODDS_FOR_CLV_AUDIT"]',
 '{"summary":"Auditoria completa de CLV, correlacion EV-CLV y significacion estadistica t-Student"}')
ON CONFLICT (workflow_id, step_number) DO NOTHING;
