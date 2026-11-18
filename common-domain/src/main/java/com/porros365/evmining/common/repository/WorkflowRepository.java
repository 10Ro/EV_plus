package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {
    Optional<WorkflowEntity> findByCode(String code);
}
