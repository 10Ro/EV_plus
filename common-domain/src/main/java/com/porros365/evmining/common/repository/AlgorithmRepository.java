package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.AlgorithmEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlgorithmRepository extends JpaRepository<AlgorithmEntity, UUID> {
    Optional<AlgorithmEntity> findByCode(String code);
}
