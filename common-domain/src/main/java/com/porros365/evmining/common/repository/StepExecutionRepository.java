package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.StepExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StepExecutionRepository extends JpaRepository<StepExecutionEntity, UUID> {
    List<StepExecutionEntity> findByExecutionIdOrderByStepNumberAsc(UUID executionId);
    Optional<StepExecutionEntity> findByExecutionIdAndStepNumber(UUID executionId, Integer stepNumber);
}
