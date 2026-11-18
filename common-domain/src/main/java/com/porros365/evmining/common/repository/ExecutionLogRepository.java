package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.ExecutionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLogEntity, UUID> {
    List<ExecutionLogEntity> findByExecutionIdOrderByTimestampAsc(UUID executionId);
}
