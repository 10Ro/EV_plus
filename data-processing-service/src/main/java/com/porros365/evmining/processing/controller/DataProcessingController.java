package com.porros365.evmining.processing.controller;

import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.processing.service.MarketCleaningProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/processing")
public class DataProcessingController {

    private final MarketCleaningProcessingService processingService;

    public DataProcessingController(MarketCleaningProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping("/step2/clean")
    public ResponseEntity<StepExecutionEventMessage> runStep2Directly(@RequestBody StepExecutionCommandMessage command) {
        return ResponseEntity.ok(processingService.executeStep2Cleaning(command));
    }
}
