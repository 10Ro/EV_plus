package com.porros365.evmining.monitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.entity.ExecutionLogEntity;
import com.porros365.evmining.common.messaging.MessagingConstants;
import com.porros365.evmining.common.messaging.MonitoringLogEventMessage;
import com.porros365.evmining.common.repository.ExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionLogMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLogMonitoringService.class);

    private final ExecutionLogRepository executionLogRepository;
    private final ObjectMapper objectMapper;

    public ExecutionLogMonitoringService(ExecutionLogRepository executionLogRepository, ObjectMapper objectMapper) {
        this.executionLogRepository = executionLogRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = MessagingConstants.QUEUE_MONITORING_LOGS)
    @Transactional
    public void onMonitoringLogEvent(MonitoringLogEventMessage event) {
        log.info("Received distributed execution log [{}] from service [{}] for execution [{}]",
                event.eventCode(), event.serviceName(), event.executionId());
        recordLogEvent(event);
    }

    @Transactional
    public ExecutionLogEntity recordLogEvent(MonitoringLogEventMessage event) {
        ExecutionLogEntity entity = new ExecutionLogEntity();
        entity.setId(event.logId() != null ? event.logId() : UUID.randomUUID());
        entity.setExecutionId(event.executionId());
        entity.setStepExecutionId(event.stepExecutionId());
        entity.setStepNumber(event.stepNumber());
        entity.setServiceName(event.serviceName());
        entity.setLevel(event.level() != null ? event.level() : "INFO");
        entity.setEventCode(event.eventCode());
        entity.setMessage(event.message());
        entity.setContextData(toJson(event.contextData()));
        entity.setTimestamp(event.timestamp() != null ? event.timestamp() : Instant.now());
        return executionLogRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ExecutionLogEntity> getLogsForExecution(UUID executionId) {
        return executionLogRepository.findByExecutionIdOrderByTimestampAsc(executionId);
    }

    private String toJson(Map<String, Object> contextData) {
        if (contextData == null || contextData.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(contextData);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
