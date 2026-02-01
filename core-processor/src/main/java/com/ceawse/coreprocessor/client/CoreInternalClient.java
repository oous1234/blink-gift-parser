package com.ceawse.coreprocessor.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "coreInternalClient", url = "https://blinkback.ru.tuna.am")
public interface CoreInternalClient {
    @PostMapping("/api/internal/v1/deals/publish")
    void sendDealToFront(@RequestBody Map<String, Object> dealData);
}