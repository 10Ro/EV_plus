package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.MiningResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MiningResultRepository extends JpaRepository<MiningResultEntity, UUID> {
    List<MiningResultEntity> findByExecutionIdOrderByStepNumberAsc(UUID executionId);
    Optional<MiningResultEntity> findByExecutionIdAndStepNumber(UUID executionId, Integer stepNumber);
}
