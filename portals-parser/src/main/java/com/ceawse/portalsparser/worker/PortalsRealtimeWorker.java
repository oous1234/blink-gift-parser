package com.ceawse.portalsparser.worker;

import com.ceawse.portalsparser.service.PortalsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortalsRealtimeWorker {
    private final PortalsService portalsService;

    @Scheduled(fixedDelay = 3000)
    public void pollEvents() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "PORT-" + traceId);
        MDC.put("context", "REALTIME");

        try {
            portalsService.processRealtimeEvents();
        } catch (Exception e) {
            log.error("Error in Portals realtime worker: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}