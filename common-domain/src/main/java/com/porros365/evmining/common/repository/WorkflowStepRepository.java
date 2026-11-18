package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.WorkflowStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStepEntity, UUID> {
    List<WorkflowStepEntity> findByWorkflowIdOrderByStepNumberAsc(UUID workflowId);
    Optional<WorkflowStepEntity> findByWorkflowIdAndStepNumber(UUID workflowId, Integer stepNumber);
}
