package com.porros365.evmining.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@Configuration
public class GatewayRoutesConfig {

    @Value("${services.ingestion.url:http://localhost:8081}")
    private String ingestionServiceUrl;

    @Value("${services.processing.url:http://localhost:8082}")
    private String processingServiceUrl;

    @Value("${services.mining.url:http://localhost:8083}")
    private String miningServiceUrl;

    @Value("${services.orchestrator.url:http://localhost:8084}")
    private String orchestratorServiceUrl;

    @Value("${services.results.url:http://localhost:8085}")
    private String resultsServiceUrl;

    @Value("${services.monitoring.url:http://localhost:8086}")
    private String monitoringServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes() {
        return route("datasets-route")
                .route(path("/api/datasets", "/api/datasets/**"), http(URI.create(ingestionServiceUrl)))
                .build()
                .and(route("processing-route")
                        .route(path("/api/processing", "/api/processing/**"), http(URI.create(processingServiceUrl)))
                        .build())
                .and(route("mining-route")
                        .route(path("/api/algorithms", "/api/algorithms/**", "/api/mining", "/api/mining/**"), http(URI.create(miningServiceUrl)))
                        .build())
                .and(route("results-compare-route")
                        .route(path("/api/executions/compare", "/api/executions/*/results", "/api/results", "/api/results/**"), http(URI.create(resultsServiceUrl)))
                        .build())
                .and(route("monitoring-logs-route")
                        .route(path("/api/executions/*/logs", "/api/monitoring", "/api/monitoring/**"), http(URI.create(monitoringServiceUrl)))
                        .build())
                .and(route("orchestrator-route")
                        .route(path("/api/auth/**", "/api/workflows", "/api/workflows/**", "/api/configurations/**", "/api/executions", "/api/executions/**"), http(URI.create(orchestratorServiceUrl)))
                        .build());
    }
}
