package com.taxplatform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Simulates an external "product classification" dependency (e.g. a remote
 * compliance/classification API) that takes a few milliseconds to respond.
 */
@Service
public class ExternalClassificationService {

    private final long latencyMs;

    public ExternalClassificationService(
            @Value("${app.external.classify-latency-ms:8}") long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public void classify(Long customerId) {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
