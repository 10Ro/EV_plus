package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
    List<ExecutionEntity> findAllByOrderByCreatedAtDesc();
}
