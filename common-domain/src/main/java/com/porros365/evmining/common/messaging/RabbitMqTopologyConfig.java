package com.porros365.evmining.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfig {

    @Bean
    public TopicExchange evMiningExchange() {
        return new TopicExchange(MessagingConstants.EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue ingestionStepsQueue() {
        return new Queue(MessagingConstants.QUEUE_INGESTION_STEPS, true);
    }

    @Bean
    public Queue processingStepsQueue() {
        return new Queue(MessagingConstants.QUEUE_PROCESSING_STEPS, true);
    }

    @Bean
    public Queue miningStepsQueue() {
        return new Queue(MessagingConstants.QUEUE_MINING_STEPS, true);
    }

    @Bean
    public Queue orchestratorEventsQueue() {
        return new Queue(MessagingConstants.QUEUE_ORCHESTRATOR_EVENTS, true);
    }

    @Bean
    public Queue monitoringLogsQueue() {
        return new Queue(MessagingConstants.QUEUE_MONITORING_LOGS, true);
    }

    @Bean
    public Binding bindIngestionQueue(Queue ingestionStepsQueue, TopicExchange evMiningExchange) {
        return BindingBuilder.bind(ingestionStepsQueue)
                .to(evMiningExchange)
                .with(MessagingConstants.ROUTING_CMD_INGESTION);
    }

    @Bean
    public Binding bindProcessingQueue(Queue processingStepsQueue, TopicExchange evMiningExchange) {
        return BindingBuilder.bind(processingStepsQueue)
                .to(evMiningExchange)
                .with(MessagingConstants.ROUTING_CMD_PROCESSING);
    }

    @Bean
    public Binding bindMiningQueue(Queue miningStepsQueue, TopicExchange evMiningExchange) {
        return BindingBuilder.bind(miningStepsQueue)
                .to(evMiningExchange)
                .with(MessagingConstants.ROUTING_CMD_MINING);
    }

    @Bean
    public Binding bindOrchestratorEventsQueue(Queue orchestratorEventsQueue, TopicExchange evMiningExchange) {
        return BindingBuilder.bind(orchestratorEventsQueue)
                .to(evMiningExchange)
                .with(MessagingConstants.ROUTING_EVENT_STATUS);
    }

    @Bean
    public Binding bindMonitoringLogsQueue(Queue monitoringLogsQueue, TopicExchange evMiningExchange) {
        return BindingBuilder.bind(monitoringLogsQueue)
                .to(evMiningExchange)
                .with(MessagingConstants.ROUTING_MONITORING_LOG);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
