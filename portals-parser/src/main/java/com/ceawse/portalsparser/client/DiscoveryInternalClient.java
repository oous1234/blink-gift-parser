package com.ceawse.portalsparser.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "discoveryInternalClient", url = "http://localhost:7781")
public interface DiscoveryInternalClient {

    @PostMapping("/internal/v1/enrichment/calculate")
    void enrich(@RequestBody EnrichmentRequest request);

    @GetMapping("/internal/v1/gifts/metadata/{slug}")
    MetadataResponse getMetadata(@PathVariable("slug") String slug);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class EnrichmentRequest {
        private String id;
        private Long timestamp;
        private String giftName;
        private String collectionAddress;
        private String model;
        private String backdrop;
        private String symbol;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataResponse {
        private String id;
        private String name;
        private Integer giftNum;
        private Integer giftMinted;
        private Integer giftTotal;
        private String model;
        private Integer modelRare;
        private String backdrop;
        private Integer backdropRare;
        private String symbol;
        private Integer symbolRare;
    }
}