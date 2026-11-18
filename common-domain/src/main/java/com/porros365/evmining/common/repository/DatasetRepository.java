package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {
    List<DatasetEntity> findAllByOrderByCreatedAtDesc();
}
