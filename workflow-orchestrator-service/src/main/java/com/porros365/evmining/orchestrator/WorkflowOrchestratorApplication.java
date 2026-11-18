package com.porros365.evmining.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.porros365.evmining.orchestrator",
        "com.porros365.evmining.common.messaging"
})
@EntityScan(basePackages = "com.porros365.evmining.common.entity")
@EnableJpaRepositories(basePackages = "com.porros365.evmining.common.repository")
public class WorkflowOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowOrchestratorApplication.class, args);
    }
}
