package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryScheduler {
    private final DiscoveryService discoveryService;

    @Scheduled(fixedDelay = 5000)
    public void pollHistory() {
        try {
            discoveryService.processHistoryStream();
        } catch (Exception e) {
            log.error("Error in history discovery task", e);
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void pollRegistry() {
        try {
            discoveryService.processRegistryStream();
        } catch (Exception e) {
            log.error("Error in registry discovery task", e);
        }
    }
}