package com.porros365.evmining.mining.controller;

import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.mining.service.DataMiningExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DataMiningController {

    private final DataMiningExecutionService dataMiningExecutionService;

    public DataMiningController(DataMiningExecutionService dataMiningExecutionService) {
        this.dataMiningExecutionService = dataMiningExecutionService;
    }

    @GetMapping("/algorithms")
    public ResponseEntity<List<Map<String, Object>>> listAlgorithms() {
        return ResponseEntity.ok(dataMiningExecutionService.listCatalogAlgorithms());
    }

    @PostMapping("/mining/execute-step")
    public ResponseEntity<StepExecutionEventMessage> executeStepDirectly(@RequestBody StepExecutionCommandMessage command) {
        return ResponseEntity.ok(dataMiningExecutionService.executeMiningStep(command));
    }
}
