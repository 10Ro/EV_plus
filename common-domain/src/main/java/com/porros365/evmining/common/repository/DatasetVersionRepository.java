package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.DatasetVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersionEntity, UUID> {
    List<DatasetVersionEntity> findByDatasetIdOrderByVersionNumberDesc(UUID datasetId);
    Optional<DatasetVersionEntity> findFirstByDatasetIdOrderByVersionNumberDesc(UUID datasetId);
}
