-- V1__init_schema.sql
-- Esquema relacional completo de las 12 entidades de trazabilidad (PostgreSQL 16)

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(120) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS datasets (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(255),
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dataset_versions (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES datasets(id),
    version_number INTEGER NOT NULL,
    stage_type VARCHAR(40) NOT NULL,
    record_count INTEGER NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    schema_version VARCHAR(16) NOT NULL DEFAULT '1.0',
    records_payload JSONB NOT NULL,
    summary_stats JSONB NOT NULL,
    parent_version_id UUID REFERENCES dataset_versions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dataset_version UNIQUE (dataset_id, version_number)
);

CREATE TABLE IF NOT EXISTS algorithms (
    id UUID PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(64) NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS algorithm_versions (
    id UUID PRIMARY KEY,
    algorithm_id UUID NOT NULL REFERENCES algorithms(id),
    version VARCHAR(32) NOT NULL,
    implementation_class VARCHAR(200) NOT NULL,
    code_commit_hash VARCHAR(64) NOT NULL,
    supports_random_seed BOOLEAN NOT NULL DEFAULT FALSE,
    parameter_schema JSONB NOT NULL,
    default_parameters JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_algorithm_version UNIQUE (algorithm_id, version)
);

CREATE TABLE IF NOT EXISTS workflows (
    id UUID PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    methodology_version VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    source_thread_url VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_steps (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    step_number INTEGER NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    objective TEXT NOT NULL,
    target_service VARCHAR(64) NOT NULL,
    default_algorithm_id UUID NOT NULL REFERENCES algorithms(id),
    depends_on_step_number INTEGER,
    input_contract JSONB NOT NULL,
    output_contract JSONB NOT NULL,
    default_parameters JSONB NOT NULL,
    validation_criteria JSONB NOT NULL,
    possible_errors JSONB NOT NULL,
    expected_results JSONB NOT NULL,
    CONSTRAINT uq_workflow_step_number UNIQUE (workflow_id, step_number)
);

CREATE TABLE IF NOT EXISTS configurations (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    methodology_version VARCHAR(32) NOT NULL,
    random_seed BIGINT NOT NULL DEFAULT 42,
    global_parameters JSONB NOT NULL,
    step_overrides JSONB NOT NULL,
    config_hash VARCHAR(64) NOT NULL,
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS executions (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    initial_dataset_version_id UUID NOT NULL REFERENCES dataset_versions(id),
    current_dataset_version_id UUID NOT NULL REFERENCES dataset_versions(id),
    configuration_id UUID NOT NULL REFERENCES configurations(id),
    methodology_version VARCHAR(32) NOT NULL,
    code_version VARCHAR(64) NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    triggered_by_user_id UUID REFERENCES users(id),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS step_executions (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES executions(id),
    workflow_step_id UUID NOT NULL REFERENCES workflow_steps(id),
    step_number INTEGER NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    algorithm_version_id UUID NOT NULL REFERENCES algorithm_versions(id),
    input_dataset_version_id UUID REFERENCES dataset_versions(id),
    output_dataset_version_id UUID REFERENCES dataset_versions(id),
    status VARCHAR(24) NOT NULL,
    independent_mode BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    random_seed BIGINT,
    parameters_used JSONB NOT NULL,
    input_summary JSONB,
    output_summary JSONB,
    error_code VARCHAR(64),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    CONSTRAINT uq_execution_step UNIQUE (execution_id, step_number)
);

CREATE TABLE IF NOT EXISTS mining_results (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES executions(id),
    step_execution_id UUID NOT NULL REFERENCES step_executions(id),
    step_number INTEGER NOT NULL,
    dataset_version_id UUID NOT NULL REFERENCES dataset_versions(id),
    algorithm_version_id UUID NOT NULL REFERENCES algorithm_versions(id),
    code_version VARCHAR(64) NOT NULL,
    random_seed BIGINT,
    metrics_summary JSONB NOT NULL,
    analytical_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS execution_logs (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES executions(id),
    step_execution_id UUID REFERENCES step_executions(id),
    step_number INTEGER,
    service_name VARCHAR(64) NOT NULL,
    level VARCHAR(16) NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    context_data JSONB,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_executions_status ON executions(status);
CREATE INDEX IF NOT EXISTS idx_step_executions_exec_id ON step_executions(execution_id);
CREATE INDEX IF NOT EXISTS idx_mining_results_exec_id ON mining_results(execution_id);
CREATE INDEX IF NOT EXISTS idx_execution_logs_exec_id ON execution_logs(execution_id);
