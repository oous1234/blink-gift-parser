package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.DiscoveryInternalClient;
import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
import com.ceawse.blinkgift.dto.GetGemsItemDto;
import com.ceawse.blinkgift.mapper.EventMapper;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import com.ceawse.blinkgift.service.HistoryService;
import com.ceawse.blinkgift.service.StateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {
    private final GetGemsApiClient apiClient;
    private final GiftHistoryRepository repository;
    private final StateService stateService;
    private final EventMapper mapper;
    private final DiscoveryInternalClient discoveryClient;

    private static final String PROCESS_ID = "GETGEMS_LIVE";
    private static final List<String> TARGET_TYPES = List.of("sold", "cancelSale", "putUpForSale");

    @Override
    public void fetchRealtimeEvents() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        MDC.put("context", "REALTIME");

        try {
            long lastTime = stateService.getState(PROCESS_ID).getLastProcessedTimestamp();
            if (lastTime == 0) lastTime = System.currentTimeMillis() - 60_000;

            GetGemsHistoryDto response = apiClient.getHistory(lastTime, null, 50, null, TARGET_TYPES, false);
            if (response == null || !response.isSuccess() || response.getResponse() == null) return;

            var items = response.getResponse().getItems();
            if (items == null || items.isEmpty()) return;

            log.info("Processing {} new GetGems history events", items.size());

            for (var item : items) {
                MDC.put("context", item.getAddress());
                try {
                    processSingleEvent(item);
                } catch (Exception e) {
                    log.error("Failed event {}: {}", item.getHash(), e.getMessage());
                }
            }

            long maxTimestamp = items.stream()
                    .mapToLong(i -> i.getTimestamp() != null ? i.getTimestamp() : 0L)
                    .max().orElse(lastTime);
            stateService.updateState(PROCESS_ID, maxTimestamp, null);

        } catch (Exception e) {
            log.error("History loop error: {}", e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    private void processSingleEvent(GetGemsItemDto item) {
        if (repository.existsByHash(item.getHash())) return;

        GiftHistoryDocument doc = mapper.toHistoryEntity(item);

        try {
            String slug = mapper.createSlug(item.getName());
            if (slug != null) {
                var meta = discoveryClient.getMetadata(slug);
                mapper.enrichHistory(doc, meta);
                log.debug("Event enriched: {} model={}", slug, doc.getModel());
            }
        } catch (Exception e) {
            log.warn("Could not enrich event {}: Discovery error", item.getName());
        }

        repository.save(doc);
        log.info("Saved enriched event: {} [{}]", doc.getName(), doc.getEventType());
    }
}