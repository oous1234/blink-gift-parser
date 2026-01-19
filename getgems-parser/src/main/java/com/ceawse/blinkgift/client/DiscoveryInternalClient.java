package com.ceawse.blinkgift.client;

import lombok.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "discoveryInternalClient", url = "http://localhost:7781")
public interface DiscoveryInternalClient {

    @PostMapping("/internal/v1/enrichment/calculate")
    void enrich(@RequestBody EnrichmentRequest request);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrichmentRequest {
        private String id;
        private Long timestamp;
        private String giftName;
        private String collectionAddress;
        private String model;
        private String backdrop;
        private String symbol;

        private Integer serialNumber;
        private Integer totalLimit;
    }
}