package com.ceawse.giftdiscovery.client;

import com.ceawse.giftdiscovery.dto.external.PythonInventoryResponse;import com.ceawse.giftdiscovery.dto.external.PythonMetadataResponse;import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "python-gateway", url = "${app.python-gateway.url:http://localhost:8082}")
public interface PythonGatewayClient {

    @GetMapping("/api/v1/inventory/live")
    PythonInventoryResponse getInventoryLive(
            @RequestParam("user_id") String userId,
            @RequestParam("offset") String offset,
            @RequestParam("limit") int limit
    );

    @GetMapping("/api/v1/metadata/fast")
    PythonMetadataResponse getMetadataFast(@RequestParam("slug") String slug);
}