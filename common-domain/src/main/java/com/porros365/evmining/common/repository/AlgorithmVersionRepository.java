package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.AlgorithmVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlgorithmVersionRepository extends JpaRepository<AlgorithmVersionEntity, UUID> {
    List<AlgorithmVersionEntity> findByAlgorithmIdOrderByCreatedAtDesc(UUID algorithmId);
    Optional<AlgorithmVersionEntity> findFirstByAlgorithmIdOrderByCreatedAtDesc(UUID algorithmId);
}
