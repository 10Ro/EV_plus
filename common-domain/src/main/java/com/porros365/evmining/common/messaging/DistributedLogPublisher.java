package com.porros365.evmining.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class DistributedLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(DistributedLogPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String serviceName;

    public DistributedLogPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        this.rabbitTemplate = rabbitTemplate;
        this.serviceName = serviceName;
    }

    public void publishLog(
            UUID executionId,
            UUID stepExecutionId,
            Integer stepNumber,
            String level,
            String eventCode,
            String message,
            Map<String, Object> contextData) {
        MonitoringLogEventMessage payload = new MonitoringLogEventMessage(
                UUID.randomUUID(),
                executionId,
                stepExecutionId,
                stepNumber,
                serviceName,
                level,
                eventCode,
                message,
                contextData != null ? contextData : Map.of(),
                Instant.now()
        );
        try {
            rabbitTemplate.convertAndSend(
                    MessagingConstants.EXCHANGE_NAME,
                    MessagingConstants.ROUTING_MONITORING_LOG,
                    payload
            );
        } catch (Exception ex) {
            log.warn("Could not publish distributed log to RabbitMQ [{}]: {}", eventCode, ex.getMessage());
        }
    }
}
