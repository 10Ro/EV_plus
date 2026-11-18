package com.porros365.evmining.common.repository;

import com.porros365.evmining.common.entity.ConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConfigurationRepository extends JpaRepository<ConfigurationEntity, UUID> {
}
