package com.porros365.evmining.common.messaging;

public final class MessagingConstants {

    public static final String EXCHANGE_NAME = "ev.mining.exchange";

    public static final String ROUTING_CMD_INGESTION = "step.command.ingestion";
    public static final String ROUTING_CMD_PROCESSING = "step.command.processing";
    public static final String ROUTING_CMD_MINING = "step.command.mining";

    public static final String ROUTING_EVENT_STATUS = "step.event.status";
    public static final String ROUTING_MONITORING_LOG = "monitoring.log.event";

    public static final String QUEUE_INGESTION_STEPS = "ingestion.steps.queue";
    public static final String QUEUE_PROCESSING_STEPS = "processing.steps.queue";
    public static final String QUEUE_MINING_STEPS = "mining.steps.queue";
    public static final String QUEUE_ORCHESTRATOR_EVENTS = "orchestrator.events.queue";
    public static final String QUEUE_MONITORING_LOGS = "monitoring.logs.queue";

    private MessagingConstants() {
    }
}
