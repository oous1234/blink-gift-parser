package com.ceawse.onchainindexer.client;

import com.ceawse.onchainindexer.dto.InternalEnrichmentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "discoveryInternalClient", url = "${app.services.discovery-url:http://localhost:7781}")
public interface DiscoveryInternalClient {
    @PostMapping("/internal/v1/enrichment/calculate")
    void calculate(@RequestBody InternalEnrichmentDto.Request request);
}